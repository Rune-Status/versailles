package io.unity.application.command

import java.net.Inet4Address

import io.unity.application.model.{WorldId, WorldSetting}

/**
  * A command to let the client switch to another game world.
  * @author Sino
  */
case class SwitchWorld(
  address: Inet4Address,
  worldId: WorldId,
  settings: Set[WorldSetting.Type]
)