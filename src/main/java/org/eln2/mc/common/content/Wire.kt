@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.thermal.*
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.mathematics.Functions.bbVec
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.animations.colors.ColorInterpolators
import org.eln2.mc.client.render.animations.colors.Utilities.colorF
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.Conventions
import org.eln2.mc.common.cells.foundation.behaviors.Extensions.withElectricalEnergyConverter
import org.eln2.mc.common.cells.foundation.behaviors.Extensions.withJouleEffectHeating
import org.eln2.mc.common.cells.foundation.objects.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.NbtExtensions.getRelativeDirection
import org.eln2.mc.extensions.NbtExtensions.putRelativeDirection
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.mathematics.Functions.lerp
import org.eln2.mc.mathematics.Functions.map
import org.eln2.mc.mathematics.Geometry.cylinderExteriorSurfaceArea
import org.eln2.mc.sim.ThermalBody
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * The [ElectricalWireObject] has a single [ResistorBundle]. The Internal Pins of the bundle are connected to each other, and
 * the External Pins are exported to other Electrical Objects.
 * */
class ElectricalWireObject : ElectricalObject(), IWailaProvider {
    private val resistors = ResistorBundle(0.05)

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance : Double = resistors.resistance
        set(value) {
            field = value
            resistors.resistance = value
        }

    val power get() = resistors.power

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistors.getOfferedResistor(directionOf(neighbour))
    }

    override fun clearComponents() {
        resistors.clear()
    }

    override fun addComponents(circuit: Circuit) {
        resistors.register(connections, circuit)
    }

    override fun build() {
        resistors.connect(connections, this)

        // Is there a better way to do this?

        // The Wire uses a bundle of 4 resistors. Every resistor's "Internal Pin" is connected to every
        // other resistor's internal pin. "External Pins" are offered to connection candidates.

        resistors.process { a ->
            resistors.process { b ->
                if (a != b) {
                    a.connect(Conventions.INTERNAL_PIN, b, Conventions.INTERNAL_PIN)
                }
            }
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        if (connections.size == 2) {
            // Straight through wire. Just give absolute value I guess since directionality is ~ meaningless for wires.

            val sampleResistor = resistors.getOfferedResistor(connections[0].direction).component as Resistor

            builder.current(abs(sampleResistor.current))
        } else {
            // Branch currents. Print them all.

            connections
                .map { (resistors.getOfferedResistor(it.direction).component as Resistor).current }
                .forEach { builder.current(it) }
        }
    }
}

data class ElectricalWireModel(val resistanceMeter: Double)

object ElectricalWireModels {
    private fun getResistance(ρ: Double, L: Double, A: Double): Double {
        return ρ * L / A
    }

    fun copper(thickness: Double): ElectricalWireModel {
        return ElectricalWireModel(getResistance(1.77 * 10e-8, 1.0, thickness))
    }
}

class ThermalWireObject(val pos: CellPos) : ThermalObject(), IWailaProvider {
    var body = ThermalBody(
        pos,
        ThermalMass(Material.COPPER),
        cylinderExteriorSurfaceArea(1.0, 0.05)
    )

    override fun offerComponent(neighbour: ThermalObject): ThermalComponentInfo {
        return ThermalComponentInfo(body)
    }

    override fun addComponents(simulator: Simulator<CellPos>) {
        simulator.add(body)
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.temperature(body.temperatureK)
        builder.energy(body.thermalEnergy)
    }
}

enum class WireType {
    Electrical,
    Thermal
}

class WireCell(pos: CellPos, id: ResourceLocation, val model: ElectricalWireModel, val type: WireType) : CellBase(pos, id) {
    init {
        if(type == WireType.Electrical) {
            behaviors
                .withElectricalEnergyConverter { electricalWire.power }
                .withJouleEffectHeating { thermalWire.body }
        }
    }

    override fun createObjectSet(): SimulationObjectSet {
        return if(type == WireType.Electrical){
            SimulationObjectSet(ElectricalWireObject().also {
                it.resistance = model.resistanceMeter / 2.0 // Divide by two because bundle creates 2 resistors
            }, ThermalWireObject(pos))
        } else {
            SimulationObjectSet(ThermalWireObject(pos))
        }
    }

    private val electricalWire get() = electricalObject as ElectricalWireObject
    private val thermalWire get() = thermalObject as ThermalWireObject

    val temperature get() = thermalWire.body.temperatureK
}

class WirePart(id: ResourceLocation, context: PartPlacementContext, cellProvider: CellProvider, val type: WireType) :
    CellPart(id, context, cellProvider) {

    companion object {
        private const val TEMPERATURE = "temperature"
        private const val CONNECTIONS = "connections"
        private const val DIRECTIONS = "directions"
        private const val DIRECTION = "dir"
        private const val COLD_LIGHT_LEVEL = 0.0
        private const val COLD_LIGHT_TEMPERATURE = 500.0
        private const val HOT_LIGHT_LEVEL = 10.0
        private const val HOT_LIGHT_TEMPERATURE = 1000.0
    }

    override val baseSize = bbVec(8.0, 2.0, 8.0)

    private val connectedDirections = HashSet<RelativeRotationDirection>()
    private var wireRenderer: WirePartRenderer? = null
    private var temperature = 0.0
    private var connectionsChanged = true

    override fun createRenderer(): IPartRenderer {
        wireRenderer = WirePartRenderer(this,
            if(type == WireType.Electrical) WireMeshSets.electricalWireMap
            else WireMeshSets.thermalWireMap,
            type)

        applyRendererState()

        return wireRenderer!!
    }

    override fun destroyRenderer() {
        super.destroyRenderer()
        wireRenderer = null
    }

    override fun onPlaced() {
        super.onPlaced()

        if (!placementContext.level.isClientSide) {
            syncChanges()
        }
    }

    override fun loadFromTag(tag: CompoundTag) {
        super.loadFromTag(tag)

        loadConnectionsTag(tag)
    }

    override fun getSaveTag(): CompoundTag? {
        val tag = super.getSaveTag() ?: return null

        saveConnections(tag)

        return tag
    }

    override fun getSyncTag(): CompoundTag {
        return CompoundTag().also { tag ->
            if(connectionsChanged) {
                saveConnections(tag)
                connectionsChanged = false
            }
            temperature = (cell as WireCell).temperature
            tag.putDouble(TEMPERATURE, temperature)
        }
    }

    override fun handleSyncTag(tag: CompoundTag) {
        if(tag.contains(DIRECTIONS)) {
            loadConnectionsTag(tag)
        }

        temperature = tag.getDouble(TEMPERATURE)
        wireRenderer?.updateTemperature(temperature)
    }

    private fun updateLight() {
        val temperature = temperature.coerceIn(COLD_LIGHT_TEMPERATURE, HOT_LIGHT_TEMPERATURE)

        val level = (lerp(
            COLD_LIGHT_LEVEL,
            HOT_LIGHT_LEVEL,
            map(temperature, COLD_LIGHT_TEMPERATURE, HOT_LIGHT_TEMPERATURE, 0.0, 1.0)) * 15)
            .toInt()
            .coerceIn(0, 15)

        updateBrightness(level)
    }

    private fun saveConnections(tag: CompoundTag) {
        val directionList = ListTag()

        connectedDirections.forEach { direction ->
            val directionTag = CompoundTag()

            directionTag.putRelativeDirection(DIRECTION, direction)

            directionList.add(directionTag)
        }

        tag.put(DIRECTIONS, directionList)
    }

    private fun loadConnectionsTag(tag: CompoundTag) {
        connectedDirections.clear()

        val directionList = tag.get(DIRECTIONS) as ListTag

        directionList.forEach { directionTag ->
            val direction = (directionTag as CompoundTag).getRelativeDirection(DIRECTION)

            connectedDirections.add(direction)
        }

        if (placementContext.level.isClientSide) {
            applyRendererState()
        }
    }

    private fun applyRendererState() {
        wireRenderer?.applyDirections(connectedDirections.toList())
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) {
        connectionsChanged = true
        connectedDirections.add(direction)
        syncAndSave()
    }

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {
        connectionsChanged = true
        connectedDirections.remove(direction)
        syncAndSave()
    }

    override fun onCellAcquired() {
        sendTemperatureUpdates()
    }

    private fun sendTemperatureUpdates() {
        if(!isAlive) {
            return
        }

        syncChanges()
        updateLight()

        EventScheduler.scheduleWorkPre(20, this::sendTemperatureUpdates)
    }
}

object WireMeshSets {
    // todo replace when models are available

    val electricalWireMap = mapOf(
        (DirectionMask.EMPTY) to PartialModels.WIRE_CROSSING_EMPTY,
        (DirectionMask.FRONT) to PartialModels.WIRE_CROSSING_SINGLE_WIRE,
        (DirectionMask.FRONT + DirectionMask.BACK) to PartialModels.WIRE_STRAIGHT,
        (DirectionMask.FRONT + DirectionMask.LEFT) to PartialModels.WIRE_CORNER,
        (DirectionMask.LEFT + DirectionMask.FRONT + DirectionMask.RIGHT) to PartialModels.WIRE_CROSSING,
        (DirectionMask.HORIZONTALS) to PartialModels.WIRE_CROSSING_FULL
    )

    val thermalWireMap = mapOf(
        (DirectionMask.EMPTY) to PartialModels.THERMAL_WIRE_CROSSING_EMPTY,
        (DirectionMask.FRONT) to PartialModels.THERMAL_WIRE_CROSSING_SINGLE_WIRE,
        (DirectionMask.FRONT + DirectionMask.BACK) to PartialModels.THERMAL_WIRE_STRAIGHT,
        (DirectionMask.FRONT + DirectionMask.LEFT) to PartialModels.THERMAL_WIRE_CORNER,
        (DirectionMask.LEFT + DirectionMask.FRONT + DirectionMask.RIGHT) to PartialModels.THERMAL_WIRE_CROSSING,
        (DirectionMask.HORIZONTALS) to PartialModels.THERMAL_WIRE_CROSSING_FULL
    )
}

class WirePartRenderer(val part: WirePart, val meshes: Map<DirectionMask, PartialModel>, val type: WireType) : IPartRenderer {
    companion object {
        private val COLD_TINT = colorF(1f, 1f, 1f, 1f)
        private val HOT_TINT = colorF(5f, 0.1f, 0.2f, 1f)
        private val COLD_TEMP = STANDARD_TEMPERATURE
        private val HOT_TEMP = Temperature.from(1000.0, ThermalUnits.CELSIUS)
        private val INTERPOLATOR = ColorInterpolators.rgbLinear()
    }

    private lateinit var multipartInstance: MultipartBlockEntityInstance
    private var modelInstance: ModelData? = null

    // Reset on every frame
    private var latestDirections = AtomicReference<List<RelativeRotationDirection>>()

    private val temperatureUpdate = AtomicUpdate<Double>()

    fun updateTemperature(value: Double) {
        temperatureUpdate.setLatest(value)
    }

    fun applyDirections(directions: List<RelativeRotationDirection>) {
        latestDirections.set(directions)
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart
    }

    override fun beginFrame() {
        selectModel()
        applyTemperatureRendering()
    }

    private fun selectModel() {
        val directions =
            latestDirections.getAndSet(null)
                ?: return

        val mask = DirectionMask.ofRelatives(directions)

        var found = false

        // Here, we search for the correct configuration by just rotating all the cases we know.
        meshes.forEach { (mappingMask, model) ->
            val match = mappingMask.matchCounterClockWise(mask)

            if (match != -1) {
                found = true

                updateInstance(model, Vector3f.YP.rotationDegrees(90f * match))

                return@forEach
            }
        }

        if (!found) {
            LOGGER.error("Wire did not handle cases: $directions")
        }

        applyTemperatureRendering()
    }

    private fun applyTemperatureRendering() {
        if(type != WireType.Thermal) {
            return
        }

        temperatureUpdate.consume {
            val temperature = it.coerceIn(COLD_TEMP.kelvin, HOT_TEMP.kelvin)
            val progress = map(temperature, COLD_TEMP.kelvin, HOT_TEMP.kelvin, 0.0, 1.0)

            modelInstance?.setColor(INTERPOLATOR.interpolate(COLD_TINT, HOT_TINT, progress.toFloat()))
        }
    }

    private fun updateInstance(model: PartialModel, rotation: Quaternion) {
        modelInstance?.delete()

        // Conversion equation:
        // let S - world size, B - block bench size
        // S = B / 16
        val size = 1.5 / 16

        // todo: it still looks a little bit off the ground, why?

        modelInstance = multipartInstance.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .translate(part.placementContext.face.opposite.normal.toVec3() * Vec3(size, size, size))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placementContext.face.rotation * part.facingRotation * rotation)
            .zeroCenter()

        multipartInstance.relightPart(part)
    }

    override fun relightModels(): List<FlatLit<*>> {
        if (modelInstance != null) {
            return listOf(modelInstance!!)
        }

        return listOf()
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
