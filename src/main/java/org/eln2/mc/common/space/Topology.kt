package org.eln2.mc.common.space

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.ageseries.libage.data.ImmutableBiMapView
import org.ageseries.libage.data.biMapOf
import org.eln2.mc.extensions.*
import java.util.function.Supplier

interface Locator<Param>
interface R3
interface SO3

data class BlockPosLocator(val pos: BlockPos) : Locator<R3>
data class IdentityDirectionLocator(val forwardWorld: Direction): Locator<SO3>
data class BlockFaceLocator(val faceWorld: Direction) : Locator<SO3>

// Got lost in the type system, for now we are using Any:
interface ILocatorSerializer {
    fun toNbt(obj: Any): CompoundTag
    fun fromNbt(tag: CompoundTag): Any
}

fun classId(c: Class<*>): String = c.canonicalName
fun locatorId(param: Class<*>, loc: Class<*>): String = "${classId(param)}: ${classId(loc)}"

val paramClassNames: ImmutableBiMapView<String, Class<*>> = biMapOf(
    classId(R3::class.java) to R3::class.java,
    classId(SO3::class.java) to SO3::class.java
)

val locatorClassNames: ImmutableBiMapView<String, Class<*>> = biMapOf(
    locatorId(R3::class.java, BlockPosLocator::class.java) to BlockPosLocator::class.java,
    locatorId(SO3::class.java, IdentityDirectionLocator::class.java) to IdentityDirectionLocator::class.java,
    locatorId(SO3::class.java, BlockFaceLocator::class.java) to BlockFaceLocator::class.java
)

val locatorSerializers: ImmutableBiMapView<Class<*>, ILocatorSerializer> = biMapOf(
    BlockPosLocator::class.java to object: ILocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply {  putBlockPos("Pos", (obj as BlockPosLocator).pos) }

        override fun fromNbt(tag: CompoundTag): BlockPosLocator =
            BlockPosLocator(tag.getBlockPos("Pos"))
    },

    IdentityDirectionLocator::class.java to object: ILocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putDirection("Forward", (obj as IdentityDirectionLocator).forwardWorld) }

        override fun fromNbt(tag: CompoundTag): Any =
            IdentityDirectionLocator(tag.getDirection("Forward"))
    },

    BlockFaceLocator::class.java to object: ILocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putDirection("Face", (obj as BlockFaceLocator).faceWorld) }

        override fun fromNbt(tag: CompoundTag): Any =
            BlockFaceLocator(tag.getDirection("Face"))
    }
)

val locatorSetFactories: ImmutableBiMapView<Class<*>, Supplier<LocatorSet<*>>> = biMapOf(
    R3::class.java to Supplier { LocatorSet<R3>() },
    SO3::class.java to Supplier { LocatorSet<SO3>() }
)

fun getLocatorSerializer(locatorClass: Class<*>): ILocatorSerializer {
    return locatorSerializers.forward[locatorClass]
        ?: error("Failed to find serializer definition for $locatorClass")
}

class LocatorSet<Param> {
    private val locators = HashMap<Class<*>, Locator<Param>>()

    fun copy(): LocatorSet<Param> {
        val result = LocatorSet<Param>()

        locators.forEach { (k, v) ->
            result.add(k, v)
        }

        return result
    }

    fun getLocators(): HashMap<Class<*>, Locator<Param>> = locators.clone() as HashMap<Class<*>, Locator<Param>>

    fun<T : Locator<Param>> withLocator(c: Class<T>, l : T): LocatorSet<Param> {
        if(locators.put(c, l) != null) {
            error("Duplicate locator $c $l")
        }

        return this
    }

    fun add(c: Class<*>, instance: Locator<*>) {
        if(locators.put(c, instance as Locator<Param>) != null) {
            error("Duplicate locator $c")
        }
    }

    inline fun<reified T : Locator<Param>> withLocator(l: T): LocatorSet<Param> = withLocator(T::class.java, l)
    fun <T : Locator<Param>> get(c: Class<T>): T? = locators[c] as? T
    inline fun<reified T : Locator<Param>> get(): T? = get(T::class.java)
    fun <T : Locator<Param>> has(c: Class<T>): Boolean = get(c) != null
    inline fun <reified T : Locator<Param>> has(): Boolean = has(T::class.java)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as LocatorSet<*>

        if(locators.size != other.locators.size) {
            return false
        }

        for(c in locators.keys) {
            val otherValue = other.locators[c]
                ?: return false

            if(otherValue != locators[c]!!){
                return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        return locators.hashCode()
    }
}

class LocationDescriptor {
    private val locatorSets = HashMap<Class<*>, LocatorSet<*>>()

    fun copy(): LocationDescriptor {
        val result = LocationDescriptor()

        locatorSets.forEach { (k, v) ->
            result.withLocatorSet(k, v.copy())
        }

        return result
    }

    fun withLocatorSet(c: Class<*>, l: LocatorSet<*>): LocationDescriptor {
        if(locatorSets.put(c, l) != null){
            error("Duplicate parameter $c $l")
        }

        return this
    }

    inline fun<reified Param> withLocatorSet(set: LocatorSet<Param>): LocationDescriptor {
        return withLocatorSet(Param::class.java, set)
    }

    fun<Param> getLocatorSet(c: Class<Param>): LocatorSet<Param>? {
        return locatorSets[c] as? LocatorSet<Param>
    }

    inline fun<reified Param> getLocatorSet(): LocatorSet<Param>? {
        return getLocatorSet(Param::class.java)
    }

    fun <Param> hasLocatorSet(c: Class<Param>): Boolean {
        return getLocatorSet(c) != null
    }

    inline fun<reified Param> hasLocatorSet(): Boolean {
        return hasLocatorSet(Param::class.java)
    }

    fun<Param> withLocator(locatorClass: Class<Locator<Param>>, paramClass: Class<Param>, l: Locator<Param>): LocationDescriptor {
        val set = locatorSets.getOrPut(paramClass) { LocatorSet<Param>() } as LocatorSet<Param>
        set.withLocator(locatorClass, l)

        return this
    }

    inline fun<reified Param> withLocator(l: Locator<Param>): LocationDescriptor {
        return withLocator(l.javaClass, Param::class.java, l)
    }

    inline fun<reified Param, reified Loc: Locator<Param>> getLocator(): Loc? {
        val set = getLocatorSet<Param>() ?: return null
        return set.get()
    }

    inline fun<reified Param, reified Loc: Locator<Param>> hasLocator(): Boolean {
        val set = getLocatorSet<Param>() ?: return false
        return set.has<Loc>()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as LocationDescriptor

        if (locatorSets != other.locatorSets) return false

        return true
    }

    override fun hashCode(): Int {
        return locatorSets.hashCode()
    }

    fun toNbt(): CompoundTag {
        val result = CompoundTag()

        val setList = ListTag()

        locatorSets.forEach { (paramClass, locatorSet) ->
            val setCompound = CompoundTag()

            setCompound.putString(
                "ParamClass",
                paramClassNames.backward[paramClass]
                    ?: error("Failed to get param name for $paramClass")
            )

            val locatorList = ListTag()

            locatorSet.getLocators().forEach { (locatorClass, locatorInst) ->
                val locatorCompound = CompoundTag()

                locatorCompound.putString(
                    "LocatorClass",
                    locatorClassNames.backward[locatorClass]
                        ?: error("Failed to get locator name for $locatorClass")
                )

                val serializer = getLocatorSerializer(locatorClass)
                locatorCompound.put("Locator", serializer.toNbt(locatorInst))
                locatorList.add(locatorCompound)
            }

            setCompound.put("Locators", locatorList)

            setList.add(setCompound)
        }

        result.put("Sets", setList)

        return result
    }

    companion object {
        fun fromNbt(compoundTag: CompoundTag): LocationDescriptor {
            val result = LocationDescriptor()

            (compoundTag.get("Sets") as ListTag).map { it as CompoundTag }.forEach { setCompound ->
                val paramClassName = setCompound.getString("ParamClass")
                val paramClass = paramClassNames.forward[paramClassName]
                    ?: error("Failed to solve param class $paramClassName")

                val set = result.locatorSets.getOrPut(paramClass) {
                    (locatorSetFactories.forward[paramClass] ?: error("Failed to get locator set factory $paramClass"))
                        .get()
                }

                (setCompound.get("Locators") as ListTag).map { it as CompoundTag }.forEach { locatorCompound ->
                    val locatorClassName = locatorCompound.getString("LocatorClass")
                    val locatorClass = locatorClassNames.forward[locatorClassName]
                        ?: error("Failed to solve locator class $locatorClassName")

                    val serializer = getLocatorSerializer(locatorClass)
                    set.add(locatorClass, serializer.fromNbt(locatorCompound.getCompound("Locator")) as Locator<*>)
                }
            }

            return result
        }
    }
}

inline fun <reified Param> LocationDescriptor.requireSp(noinline message: (() -> Any)? = null) {
    if(message != null) require(this.hasLocatorSet<Param>(), message)
    else require(this.hasLocatorSet<Param>()) { "Requirement of ${Param::class.java} is not fulfilled"}
}

inline fun<reified Param, reified Loc: Locator<Param>> LocationDescriptor.requireLocator(noinline message: (() -> Any)? = null): Loc {
    if(message != null) require(this.hasLocator<Param, Loc>(), message)
    else require(this.hasLocator<Param, Loc>()) { "Requirement of ${Param::class.java}, ${Loc::class.java} is not fulfilled"}

    return this.getLocator<Param, Loc>()!!
}

// Wrappers:

fun LocationDescriptor.requireBlockPosLoc(message: (() -> Any)? = null): BlockPos =
    this.requireLocator<R3, BlockPosLocator>(message).pos


fun LocationDescriptor.requireBlockFaceLoc(message: (() -> Any)? = null): Direction =
    this.requireLocator<SO3, BlockFaceLocator>(message).faceWorld


fun LocationDescriptor.requireIdentityDirLoc(message: (() -> Any)? = null): Direction =
    this.requireLocator<SO3, IdentityDirectionLocator>(message).forwardWorld

fun interface ILocationRelationshipRule {
    fun acceptsRelationship(descriptor: LocationDescriptor, target: LocationDescriptor): Boolean
}

class LocatorRelationRuleSet {
    private val rules = ArrayList<ILocationRelationshipRule>()

    fun with(rule: ILocationRelationshipRule): LocatorRelationRuleSet {
        rules.add(rule)
        return this
    }

    fun accepts(descriptor: LocationDescriptor, target: LocationDescriptor): Boolean {
        return rules.all { r -> r.acceptsRelationship(descriptor, target) }
    }
}

fun LocatorRelationRuleSet.withDirectionActualRule(mask: DirectionMask): LocatorRelationRuleSet {
    return this.with { a, b ->
        mask.has(a.findDirActualOrNull(b) ?: return@with false)
    }
}

fun LocationDescriptor.findDirActualOrNull(other: LocationDescriptor) : RelativeDirection? {
    val actualPosWorld = this.getLocator<R3, BlockPosLocator>() ?: return null
    val actualIdWorld = this.getLocator<SO3, IdentityDirectionLocator>() ?: return null
    val actualFaceWorld = this.getLocator<SO3, BlockFaceLocator>() ?: return null
    val targetPosWorld = other.getLocator<R3, BlockPosLocator>() ?: return null

    return RelativeDirection.fromForwardUp(
        actualIdWorld.forwardWorld,
        actualFaceWorld.faceWorld,
        actualPosWorld.pos.directionTo(targetPosWorld.pos)
            ?: return null
    )
}

fun LocationDescriptor.findDirActual(other: LocationDescriptor): RelativeDirection {
    return this.findDirActualOrNull(other) ?: error("Failed to get relative rotation direction")
}
