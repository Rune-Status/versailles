package io.versailles.application.launch

import java.net.InetSocketAddress
import java.nio.file.{Path, Paths}

import com.redis.RedisClient
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.versailles.application.component.account.{AccountService, PostgresAccountRepository}
import io.versailles.application.component.auth.{AuthenticationService, BCryptPasswordMatcher, PasswordMatcher}
import io.versailles.application.component.character.{CharacterCache, CharacterService, PostgresCharacterRepository, RedisCharacterCache}
import io.versailles.application.component.clan.{ClanService, PostgresClanRepository}
import io.versailles.application.component.friend.{FriendService, PostgresFriendRepository, PostgresIgnoredRepository}
import io.versailles.application.component.login.LoginService
import io.versailles.application.component.middleware.Server
import io.versailles.application.component.middleware.encoding.{ServiceConnectDecoder, ServiceResponseEncoder}
import io.versailles.application.component.middleware.handler.ServiceConnectHandler
import io.versailles.application.model.{ClientVersion, CredentialBlockKeySet}
import io.versailles.application.storage.FileBundle
import io.versailles.application.storage.Storage
import io.versailles.domain.model._
import org.slf4j.LoggerFactory
import scalaz.zio.duration._
import scalaz.zio.{App, IO, system}

/**
  * The main entry point to this game server application.
  * @author Sino
  */
object VersaillesLauncher extends App {
  /** The [[Path]] to the main Versailles configuration file. */
  val pathToVersaillesConfig = Paths.get("resources/configs/versailles.conf")

  /** Logs information and errors to either console or file. */
  val logger = LoggerFactory.getLogger(VersaillesLauncher.getClass)

  override def run(args: List[String]) =
    launch
      .attempt
      .map(_.fold(failure => exitWhenDone, success => exitNow))

  /** Returns the exit status for delayed system exit. */
  private def exitWhenDone = ExitStatus.ExitWhenDone(code = 1, timeout = 1.minute)

  /** Returns the exit status for immediate system exit. */
  private def exitNow = ExitStatus.ExitNow(code = 0)

  /** Sets up the application components, loads resources into memory and boots
    * up the server to deal with requests. */
  private def launch: IO[Exception, Unit] =
    for {
      _               <- info("Starting Versailles...")

      config          <- loadVersaillesConfig(pathToVersaillesConfig)
      _               <- info(s"Expected client version: ${config.clientVersion}")

      storage           <- loadAssetStorage(config.storagePath)
      _               <- info(s"Loaded asset storage that's located at ${config.storagePath}")

      archiveCount    <- IO.succeed(storage.getArchiveCount)

      redisClient     <- connectToRedis

      accounts        <- setupAccountComponent(new PostgresAccountRepository)
      characters      <- setupCharacterComponent(new PostgresCharacterRepository, new RedisCharacterCache)

      friends         <- setupFriendComponent(new PostgresFriendRepository, new PostgresIgnoredRepository)
      clans           <- setupClanComponent(new PostgresClanRepository)

      auth            <- setupAuthenticationComponent(accounts, new BCryptPasswordMatcher)
      login           <- setupLoginComponent(auth, characters)

      server          <- setupServerComponent(login, config.clientVersion, storage, archiveCount, config.credentialsKeySet)

      _               <- server.awaitTermination
    } yield ()

  /** Constructs a new [[CharacterService]]. */
  private def setupCharacterComponent(permanentStorage: CharacterRepository, cache: CharacterCache) =
    IO.succeed(new CharacterService(permanentStorage, cache))

  /** Constructs a new [[AccountService]]. */
  private def setupAccountComponent(repository: AccountRepository) =
    IO.succeed(new AccountService(repository))

  /** Constructs a new [[ClanService]]. */
  private def setupClanComponent(repository: ClanRepository) =
    IO.succeed(new ClanService(repository))

  /** Constructs a new [[FriendService]]. */
  private def setupFriendComponent(friendRepo: FriendRepository, ignoreRepo: IgnoredRepository) =
    IO.succeed(new FriendService(friendRepo, ignoreRepo))

  /** Constructs a new [[AuthenticationService]]. */
  private def setupAuthenticationComponent(accounts: AccountService, passwordHasher: PasswordMatcher) =
    IO.succeed(new AuthenticationService(accounts, passwordHasher))

  /** Constructs a new [[LoginService]]. */
  private def setupLoginComponent(auth: AuthenticationService, characters: CharacterService) =
    IO.succeed(new LoginService(auth, characters))

  /** Sets up the [[Server]] component. */
  // TODO fix createChannelInitializer() terribly long parameter list
  private def setupServerComponent(login: LoginService, expectedVersion: ClientVersion, assetStorage: Storage, archiveCount: Int, credentialsKeySet: CredentialBlockKeySet) =
    for {
      server        <- Server.create

      eventLoop     <- server.createDefaultEventLoopGroup
      bootstrap     <- server.createBootstrap(eventLoop, eventLoop, createChannelInitializer(login, expectedVersion, assetStorage, archiveCount, credentialsKeySet))

      port          <- getServerPort
      address       <- IO.succeed(new InetSocketAddress(port))

      _             <- server.ignite(bootstrap, address)
      _             <- info(s"Server ready to accept incoming connections at $address")
    } yield server

  /** Produces a new [[ChannelInitializer]]. */
  // TODO fix ServiceConnectHandler's terribly long dependency list
  private def createChannelInitializer(login: LoginService, expectedVersion: ClientVersion, assetStorage: Storage, archiveCount: Int, credentialsKeySet: CredentialBlockKeySet) =
    new ChannelInitializer[SocketChannel]() {
      override def initChannel(ch: SocketChannel) = {
        ch.pipeline().addLast("decoder", new ServiceConnectDecoder)
        ch.pipeline().addLast("encoder", new ServiceResponseEncoder)
        ch.pipeline().addLast("handler", new ServiceConnectHandler(login, assetStorage, expectedVersion, archiveCount, credentialsKeySet))
      }
    }

  /** Attempts to connect to a Redis server using the details stored in the
    * environment variables. */
  private def connectToRedis =
    for {
      memStoreHost  <- getInMemoryStoreHost
      memStorePort  <- getInMemoryStorePort

      redisClient   <- createRedisClient(memStoreHost, memStorePort)
      _             <- info(s"Connected to in-memory store at $memStoreHost:$memStorePort")
    } yield redisClient

  /** Attempts to load a [[VersaillesConfig]] from a local configuration file
    * on disk. */
  private def loadVersaillesConfig(path: Path) =
    VersaillesConfig.load(path)

  /** Attempts to synchronously load the asset binary storage into memory. */
  private def loadAssetStorage(path: Path) =
    IO.sync(new Storage(FileBundle.open(path.toFile)))

  /** Produces a new [[RedisClient]] which is to automatically connect to a
    * local or remote Redis server using the specified details. */
  private def createRedisClient(host: String, port: Int) =
    IO.sync(new RedisClient(host, port))

  /** Retrieves the host of the in-memory store from the environment variables.
    * If the environment variable isn't set, a default is returned. */
  private def getInMemoryStoreHost =
    system.env("VERSAILLES_MEMSTORE_HOST").map(_.getOrElse("localhost"))

  /** Retrieves the port of the in-memory store from the environment variables.
    * If the environment variable isn't set, a default is returned. */
  private def getInMemoryStorePort =
    system
      .env("VERSAILLES_MEMSTORE_PORT")
      .map(_.getOrElse("6379"))
      .map(_.toInt)

  /** Retrieves the port of the middleware server from the environment
    * variables. If the environment variable isn't set, a default is
    * returned. */
  private def getServerPort =
    system
      .env("VERSAILLES_PORT")
      .map(_.getOrElse("43594"))
      .map(_.toInt)

  /** Logs the given info message. */
  private def info(message: String) =
    IO.sync(logger.info(message))

  /** Logs the given [[Throwable]] as a system failure. */
  private def error(t: Throwable) =
    IO.sync(logger.error("Failed to boot Versailles up", t))
}