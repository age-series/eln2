package org.eln2.sim.electrical.mna

import org.eln2.debug.dprintln

/**
 * A "node", in MNA; the non-resistive connections between [Component]s.
 *
 * Aside from identifying [Component]s' connections, the Nodes' potentials (relative to [Circuit.ground]) are computed as a result of the MNA algorithm.
 */
open class Node(var circuit: Circuit) : IDetail {
	/**
	 * The potential of this node, in Volts, relative to ground (as defined by the [Circuit]); an output of the simulation.
	 */
	open var potential: Double = 0.0
	/**
	 * The index of this node into the [Circuit]'s matrices and vectors.
	 */
	open var index: Int = -1  // Assigned by Circuit
	/**
	 * True if this node is ground (defined to have zero potential).
	 */
	open val isGround = false
	var name = "node"

	override fun detail(): String {
		return "[node val: $potential]"
	}

	/** Determine which node should prevail when two are merged in a Circuit.

	   This is mostly so subclasses of Node (if any) can maintain their existence when merged. The Node returning the
	   higher value is chosen; if both are equal (commonly the case), one is chosen arbitrarily.
	 */

	open fun mergePrecedence(other: Node): Int = 0

	fun stampResistor(to: Node, r: Double) {
		dprintln("N.sR $to $r")
		circuit.stampResistor(index, to.index, r)
	}
}

/**
 * A Node subclass for representing [Circuit.ground], with a higher [mergePrecedence], always [potential] 0V and [index] -1 (not assigned).
 * 
 * You can detect this via [isGround], which is true for these instances.
 */
class GroundNode(circuit: Circuit) : Node(circuit) {
	override var potential: Double
		get() = 0.0
		set(value) {}

	override var index: Int
		get() = -1
		set(value) {}

	override val isGround = true

	override fun mergePrecedence(other: Node): Int = 100
}

/**
 * A "NodeRef", which simply refers to an underlying [Node].
 *
 * This additional level of indirection allows nodes between [Components] to be united normally, _and_ for a single connection to [Circuit.ground] to connect _all_ connected Components to ground, even if the connections between Components were established earlier. In this way, it resembles a simpler version of [space.Set].
 */
data class NodeRef(var node: Node)
