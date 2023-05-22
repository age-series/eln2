package org.eln2.mc.sim

import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.space.BlockPosLocator
import org.eln2.mc.common.space.R3
import org.eln2.mc.common.space.requireLocator
import java.util.concurrent.ConcurrentHashMap

data class EnvironmentInformation(
    val temperature: Temperature,
    val airThermalConductivity: Double)

object BiomeEnvironments {
    private val biomes = ConcurrentHashMap<Biome, EnvironmentInformation>()

    fun get(level: Level, pos: CellPos): EnvironmentInformation {
        val biome = level.getBiome(pos.descriptor.requireLocator<R3, BlockPosLocator> {
            "Biome Environments need a block pos locator"
        }.pos).value()

        val temperature = Datasets.MINECRAFT_TEMPERATURE_CELSIUS.evaluate(biome.baseTemperature.toDouble())

        return biomes.computeIfAbsent(biome) {
            return@computeIfAbsent EnvironmentInformation(
                Temperature.from(temperature, ThermalUnits.CELSIUS),
                Datasets.AIR_THERMAL_CONDUCTIVITY.evaluate(temperature)
            )
        }
    }
}
