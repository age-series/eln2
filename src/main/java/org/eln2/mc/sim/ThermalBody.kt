package org.eln2.mc.sim

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.extensions.appendBody
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder

class ThermalBody(var thermal: ThermalMass, var area: Double) : WailaEntity {
    var temp: Temperature
        get() = thermal.temperature
        set(value) { thermal.temperature = value }

    var tempK: Double
        get() = temp.kelvin
        set(value) { thermal.temperature = Temperature(value) }

    var energy: Double
        get() = thermal.energy
        set(value) { thermal.energy = value }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        thermal.appendBody(builder, config)
    }

    companion object {
        fun createDefault(): ThermalBody {
            return ThermalBody(ThermalMass(Material.COPPER), 0.5)
        }
    }
}
