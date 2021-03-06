package io.versailles.application.model

/**
  * References a folder within the RuneScape game cache.
  * @author Sino
  */
case class FolderId(private val value: Int) extends AnyVal {
  def toValue = value
}
