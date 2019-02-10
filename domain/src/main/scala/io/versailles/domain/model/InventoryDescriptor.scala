package io.versailles.domain.model

/**
  * Describes an inventory.
  * @author Sino
  */
case class InventoryDescriptor(
  id: InventoryId,
  capacity: InventoryCapacity
)