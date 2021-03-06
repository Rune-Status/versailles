package io.versailles.application.model

/**
  * References an archive within the RuneScape game cache.
  * @author Sino
  */
case class ArchiveId(private val value: Int) extends AnyVal {
  def toValue = value
}
