package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.Operator
import org.eln2.compute.asmComputer.State

open class SubI: Operator() {
	override val OPCODE = "subi"
	override val MIN_ARGS = 2
	override val MAX_ARGS = 2
	override val COST = 0.0
	override fun run(opList: List<String>, asmComputer: AsmComputer) {
		if (opList[0] !in asmComputer.intRegisters) {
			asmComputer.currState = State.Errored
			asmComputer.currStateReasoning = "Nonexistent Register {}".format(opList[0])
			return
		}
		var result = asmComputer.intRegisters[opList[0]]?: 0
		opList.drop(1).map{
			if (it in asmComputer.intRegisters) {
				asmComputer.intRegisters[it]?: 0
			} else {
				val v = it.toIntOrNull()
				if (v == null) {
					asmComputer.currState = State.Errored
					asmComputer.currStateReasoning = "{} is not a value or register"
					return
				}
				v
			}
		}.forEach { result -= it }
		asmComputer.intRegisters[opList[0]] = result
	}
}

open class SubD: Operator() {
	override val OPCODE = "subd"
	override val MIN_ARGS = 2
	override val MAX_ARGS = 2
	override val COST = 0.0
	override fun run(opList: List<String>, asmComputer: AsmComputer) {
		if (opList[0] !in asmComputer.doubleRegisters) {
			asmComputer.currState = State.Errored
			asmComputer.currStateReasoning = "Nonexistent Register {}".format(opList[0])
			return
		}
		var result = asmComputer.doubleRegisters[opList[0]]?: 0.0
		opList.drop(1).map{
			if (it in asmComputer.doubleRegisters) {
				asmComputer.doubleRegisters[it]?: 0.0
			} else {
				val v = it.toDoubleOrNull()
				if (v == null) {
					asmComputer.currState = State.Errored
					asmComputer.currStateReasoning = "{} is not a value or register"
					return
				}
				v
			}
		}.forEach { result -= it }
		asmComputer.doubleRegisters[opList[0]] = result
	}
}
