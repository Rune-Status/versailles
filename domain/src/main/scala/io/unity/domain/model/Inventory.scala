package io.unity.domain.model

object Inventory {
  /** Constructs a new [[Inventory]] using the given [[InventoryDescriptor]]. */
  def apply(descriptor: InventoryDescriptor, stackMode: StackMode.Type) =
    Inventory(
      descriptor = descriptor,
      stackMode = stackMode,
      slots = Vector.tabulate(descriptor.capacity.toValue)(id => InventorySlot(id, None))
    )
}

/**
  * An inventory is a fixed-sized collection of [[Item]]s placed in
  * [[InventorySlot]]s.
  * @author Sino
  */
case class Inventory private(descriptor: InventoryDescriptor, stackMode: StackMode.Type, private val slots: Vector[InventorySlot]) {
  // TODO
}