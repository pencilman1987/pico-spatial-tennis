package com.example.spatialtennis.spatial.scene

import com.example.spatialtennis.domain.model.AiArchetype
import com.example.spatialtennis.domain.model.GamePhase
import com.example.spatialtennis.domain.model.TennisGameState
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.controller.ControllerPose

class TennisScene {
    val arenaRoot = Entity()
    val trackedRacketRoot = Entity()

    private val materials = mutableMapOf<MaterialKey, PhysicallyBasedMaterial>()
    private val meshes = mutableMapOf<MeshKey, MeshResource>()
    private val textures =
        mapOf(
            TextureKind.COURT to loadTexture("textures/island_clay_v3.png"),
            TextureKind.JERSEY to loadTexture("textures/athlete_knit_v3.png"),
        ).mapNotNull { (kind, texture) -> texture?.let { kind to it } }.toMap()
    // Every athlete is authored facing local +Z. Match opposition is expressed only through
    // root yaw, so faces, tails, hands and rackets can never disagree about forward direction.
    private val playerRoot = createCharacter(CharacterPalette.player)
    private var activeAiArchetype: AiArchetype? = null
    private var activeAiRoot: CharacterRig? = null
    private val menuAiPreviews = mutableMapOf<AiArchetype, MenuPreviewRig>()
    private val deferredBuildSteps = ArrayDeque<() -> Unit>()
    private val ball = sphere(BALL_RADIUS, Palette.ball, Surface.EMISSIVE)
    private val landingRing = torus(LANDING_RING_RADIUS, 0.4f, Palette.energy, Surface.EMISSIVE)
    private val trailGhosts = List(TRAIL_COUNT) { sphere(TRAIL_GHOST_RADIUS, Palette.gold, Surface.EMISSIVE) }
    private val skillReadyRing = torus(SKILL_RING_RADIUS, 0.52f, Palette.energy, Surface.EMISSIVE)
    private val impactRing = torus(IMPACT_RING_RADIUS, 0.32f, Palette.white, Surface.EMISSIVE)
    private val trackedRacket = createRacket(Palette.gold)
    private val trailHistory = arrayOfNulls<Vector3>(TRAIL_COUNT)
    private var trailCursor = -1
    private var trailSize = 0
    private var lastTrailPosition: Vector3? = null
    private var lastTrailRallyCount = -1
    private var lastRallyCount = 0
    private var impactStartedNanos = 0L
    private var lastPlayerX = 0f
    private var lastAiX = 0f
    private var latestGameState: TennisGameState? = null
    private var latestSelectedOpponent = AiArchetype.SPEEDSTER
    private var latestControllerPose: ControllerPose? = null

    init {
        arenaRoot.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0.42f, -2.35f))
            setScaleVector(Vector3(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE))
        }
        createArenaCore()
        arenaRoot.addChild(playerRoot.root)
        arenaRoot.addChild(ball)
        arenaRoot.addChild(landingRing)
        trailGhosts.forEach {
            it.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
            arenaRoot.addChild(it)
        }
        skillReadyRing.components[TransformComponent::class.java]?.apply {
            setEulerAngles(EulerAngles(90f, 0f, 0f))
            setScaleVector(Vector3.ZERO)
        }
        impactRing.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        arenaRoot.addChild(skillReadyRing)
        arenaRoot.addChild(impactRing)

        trackedRacketRoot.addChild(trackedRacket.root)
        trackedRacketRoot.components[TransformComponent::class.java]?.setScaleVector(Vector3(0f, 0f, 0f))
        trackedRacket.root.components[TransformComponent::class.java]?.apply {
            setScaleVector(Vector3(0.42f, 0.42f, 0.42f))
            setEulerAngles(EulerAngles(0f, 0f, 90f))
        }
        enqueueDeferredSceneDetails()
    }

    /** Adds one optional visual group per cadence tick so the first menu is not blocked. */
    fun buildNextDeferredBatch(): Boolean {
        if (deferredBuildSteps.isEmpty()) return false
        deferredBuildSteps.removeFirst().invoke()
        latestGameState?.let { update(it, latestSelectedOpponent, latestControllerPose) }
        return deferredBuildSteps.isNotEmpty()
    }

    fun update(
        state: TennisGameState,
        selectedOpponent: AiArchetype,
        rightControllerPose: ControllerPose?,
    ) {
        latestGameState = state
        latestSelectedOpponent = selectedOpponent
        latestControllerPose = rightControllerPose
        val visualSeconds = System.nanoTime() / 1_000_000_000.0
        val playerLean = ((state.player.position.x - lastPlayerX) * -90f).coerceIn(-9f, 9f)
        val isMenu = state.phase == GamePhase.MENU
        val playerSceneZ = sceneZ(state.player.position.z)
        playerRoot.root.components[TransformComponent::class.java]?.apply {
            if (isMenu) {
                setPosition(
                    Vector3(
                        2.45f,
                        0.14f + kotlin.math.sin(visualSeconds * 3.2).toFloat() * 0.025f,
                        playerSceneZ - 2.4f,
                    ),
                )
                // The roster is a presentation pose: all four animals face the user.
                setEulerAngles(EulerAngles(0f, ROSTER_YAW, 0f))
                setScaleVector(playerRoot.uniformScale(0.92f))
            } else {
                setPosition(
                    Vector3(
                        state.player.position.x,
                        0.12f + kotlin.math.sin(visualSeconds * 3.2).toFloat() * 0.025f,
                        playerSceneZ,
                    ),
                )
                // Near-court player faces scene -Z, directly toward the net and far-court AI.
                setEulerAngles(EulerAngles(0f, yawToFaceNet(playerSceneZ), playerLean))
                setScaleVector(playerRoot.uniformScale())
            }
        }
        lastPlayerX = state.player.position.x
        playerRoot.racket.components[TransformComponent::class.java]?.apply {
            setEulerAngles(EulerAngles(0f, 0f, -35f + swingAngle(state.player.swingTimer)))
        }

        if (!isMenu && (activeAiArchetype != state.aiArchetype || activeAiRoot == null)) {
            activeAiRoot?.root?.removeFromParent()
            activeAiRoot?.root?.destroy()
            activeAiArchetype = state.aiArchetype
            activeAiRoot = createCharacter(CharacterPalette.forAi(state.aiArchetype))
            arenaRoot.addChild(activeAiRoot!!.root)
        }
        val aiLean = ((state.ai.position.x - lastAiX) * -90f).coerceIn(-9f, 9f)
        val aiSceneZ = sceneZ(state.ai.position.z)
        activeAiRoot?.root?.components?.get(TransformComponent::class.java)?.apply {
            if (isMenu) {
                setScaleVector(Vector3.ZERO)
            } else {
                setPosition(
                    Vector3(
                        state.ai.position.x,
                        0.12f + kotlin.math.sin(visualSeconds * 3.5 + 1.2).toFloat() * 0.022f,
                        aiSceneZ,
                    ),
                )
                // Far-court AI keeps local +Z, facing the net and the near-court player.
                setEulerAngles(EulerAngles(0f, yawToFaceNet(aiSceneZ), aiLean))
                setScaleVector(activeAiRoot!!.uniformScale())
            }
        }
        activeAiRoot?.racket?.components?.get(TransformComponent::class.java)?.apply {
            setEulerAngles(EulerAngles(0f, 0f, -35f + swingAngle(state.ai.swingTimer)))
        }
        menuAiPreviews.entries.forEachIndexed { index, (archetype, preview) ->
            preview.root.components[TransformComponent::class.java]?.apply {
                if (isMenu) {
                    val isSelected = archetype == selectedOpponent
                    val previewX = MENU_AI_START_X + index * MENU_AI_SPACING_X
                    val previewBob =
                        kotlin.math.sin(visualSeconds * (3.1 + index * 0.18) + index).toFloat() * 0.018f
                    setPosition(
                        Vector3(
                            previewX,
                            0.12f + previewBob + if (isSelected) 0.08f else 0f,
                            aiSceneZ + if (isSelected) 0.48f else 0f,
                        ),
                    )
                    setEulerAngles(
                        EulerAngles(
                            0f,
                            0f,
                            if (isSelected) {
                                kotlin.math.sin(visualSeconds * 1.7).toFloat() * 1.2f
                            } else {
                                kotlin.math.sin(visualSeconds * 1.2 + index).toFloat() * 2.2f
                            },
                        ),
                    )
                    setScaleVector(
                        preview.uniformScale(
                            if (isSelected) MENU_AI_SELECTED_SCALE else MENU_AI_UNSELECTED_SCALE,
                        ),
                    )
                } else {
                    setScaleVector(Vector3.ZERO)
                }
            }
        }
        lastAiX = state.ai.position.x

        setPosition(ball, state.ball.position.x, state.ball.position.y, sceneZ(state.ball.position.z))
        val ballScale = BALL_RADIUS * if (state.ball.flight?.isSkillShot == true) 1.55f else 1f
        ball.components[TransformComponent::class.java]?.setScaleVector(Vector3(ballScale, ballScale, ballScale))
        val bounce = state.ball.flight?.bounce
        landingRing.components[TransformComponent::class.java]?.apply {
            if (bounce == null) {
                setScaleVector(Vector3(0f, 0f, 0f))
            } else {
                setPosition(Vector3(bounce.x, 0.13f, sceneZ(bounce.z)))
                setScaleVector(Vector3(LANDING_RING_RADIUS, LANDING_RING_RADIUS, LANDING_RING_RADIUS))
                setEulerAngles(EulerAngles(90f, 0f, 0f))
            }
        }
        updateTrail(state)
        updateImpactAndSkillEffects(state, visualSeconds)

        trackedRacketRoot.components[TransformComponent::class.java]?.apply {
            if (rightControllerPose == null) {
                setScaleVector(Vector3(0f, 0f, 0f))
            } else {
                setScaleVector(Vector3(1f, 1f, 1f))
                val pose = rightControllerPose
                setPosition(pose.position)
                setQuaternion(pose.rotation)
            }
        }
    }

    fun destroy() {
        arenaRoot.destroy()
        trackedRacketRoot.destroy()
        materials.values.forEach { it.close() }
        materials.clear()
        textures.values.forEach { it.close() }
        meshes.values.forEach { it.close() }
        meshes.clear()
    }

    private fun createArenaCore() {
        // Keep the first synchronous batch below the Stage entity budget: playable court first,
        // decorative storybook groups are appended one at a time after the first frame.
        arenaRoot.addChild(cylinder(0.46f, 6.25f, Palette.islandSoil, Surface.MATTE).at(0f, -0.31f, 0f))
        arenaRoot.addChild(box(11.4f, 0.22f, 14.7f, Palette.grass, Surface.MATTE).at(0f, -0.08f, 0f))
        arenaRoot.addChild(box(8.15f, 0.18f, 13.15f, Palette.woodLight, Surface.SATIN).at(0f, 0.02f, 0f))
        arenaRoot.addChild(
            box(7.3f, 0.09f, 12.3f, Palette.clay, Surface.SATIN, TextureKind.COURT).at(0f, 0.14f, 0f),
        )

        fun line(width: Float, depth: Float, x: Float, z: Float) {
            arenaRoot.addChild(box(width, 0.035f, depth, Palette.cream, Surface.GLOSS).at(x, 0.2f, z))
        }
        line(7.3f, 0.08f, 0f, -6.1f)
        line(7.3f, 0.08f, 0f, 6.1f)
        line(0.08f, 12.2f, -3.61f, 0f)
        line(0.08f, 12.2f, 3.61f, 0f)
        line(7.3f, 0.055f, 0f, 0f)
        line(7.3f, 0.055f, 0f, -3.05f)
        line(7.3f, 0.055f, 0f, 3.05f)
        line(0.06f, 6.8f, 0f, 0f)

        arenaRoot.addChild(box(7.65f, 0.42f, 0.028f, Palette.net, Surface.NET).at(0f, 0.45f, 0f))
        arenaRoot.addChild(box(7.72f, 0.07f, 0.06f, Palette.cream, Surface.GLOSS).at(0f, 0.68f, 0f))
        arenaRoot.addChild(box(0.12f, 0.8f, 0.12f, Palette.woodDark, Surface.SATIN).at(-3.85f, 0.51f, 0f))
        arenaRoot.addChild(box(0.12f, 0.8f, 0.12f, Palette.woodDark, Surface.SATIN).at(3.85f, 0.51f, 0f))
    }

    private fun enqueueDeferredSceneDetails() {
        AiArchetype.entries.forEach { archetype ->
            deferredBuildSteps.addLast {
                if (menuAiPreviews.containsKey(archetype)) return@addLast
                val preview = createMenuPreviewCharacter(CharacterPalette.forAi(archetype))
                menuAiPreviews[archetype] = preview
                arenaRoot.addChild(preview.root)
            }
        }

        deferredBuildSteps.addLast(::createBleachers)
        val spectatorColors =
            listOf(
                Palette.rabbitFur,
                Palette.bearFur,
                Palette.foxFur,
                Palette.sky,
                Palette.honey,
                Palette.mint,
            )
        spectatorColors.forEachIndexed { index, fur ->
            deferredBuildSteps.addLast { createSpectatorAt(index, fur) }
        }
        deferredBuildSteps.addLast(::createKiosk)
        listOf(
            Vector3(-5.0f, 0f, -5.5f),
            Vector3(-5.15f, 0f, 5.25f),
            Vector3(5.15f, 0f, -5.5f),
            Vector3(5.3f, 0f, 5.35f),
        ).forEachIndexed { index, position ->
            deferredBuildSteps.addLast {
                createTree(if (index % 2 == 0) Palette.leafDark else Palette.leafLight).also {
                    it.components[TransformComponent::class.java]?.setPosition(position)
                    arenaRoot.addChild(it)
                }
            }
        }
    }

    private fun createBleachers() {
        // Wooden bleachers and six lightweight animal spectators create a readable crowd
        // without introducing skinned meshes or extra textures.
        arenaRoot.addChild(box(1.38f, 0.34f, 9.2f, Palette.woodDark, Surface.MATTE).at(4.8f, 0.22f, 0f))
        arenaRoot.addChild(box(1.34f, 0.32f, 7.8f, Palette.woodLight, Surface.SATIN).at(5.05f, 0.58f, 0f))
        arenaRoot.addChild(box(1.3f, 0.28f, 6.3f, Palette.cream, Surface.SATIN).at(5.28f, 0.9f, 0f))
    }

    private fun createSpectatorAt(
        index: Int,
        fur: Color4,
    ) {
        val row = index % 3
        val z = -3.2f + (index / 3) * 6.4f + row * 0.12f
        val x = 4.72f + row * 0.24f
        val y = 0.75f + row * 0.3f
        createSpectator(fur).also {
            it.components[TransformComponent::class.java]?.setPosition(Vector3(x, y, z))
            arenaRoot.addChild(it)
        }
    }

    private fun createKiosk() {
        // Club kiosk balances the stand, while clustered trees frame the island edges.
        arenaRoot.addChild(box(1.45f, 1.15f, 2.4f, Palette.cream, Surface.MATTE).at(-5.0f, 0.5f, 0.6f))
        val kioskRoof = box(1.85f, 0.18f, 2.75f, Palette.coral, Surface.SATIN).at(-5.0f, 1.17f, 0.6f)
        kioskRoof.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, -7f))
        arenaRoot.addChild(kioskRoof)
        arenaRoot.addChild(box(0.78f, 0.58f, 0.05f, Palette.sky, Surface.GLOSS).at(-4.24f, 0.56f, 0.6f))
    }

    private fun createTree(leafColor: Color4): Entity =
        Entity().apply {
            addChild(cylinder(1.15f, 0.18f, Palette.woodDark, Surface.MATTE).at(0f, 0.55f, 0f))
            addChild(sphere(0.72f, leafColor, Surface.MATTE).at(0f, 1.25f, 0f))
            addChild(sphere(0.48f, Palette.leafLight, Surface.MATTE).at(-0.34f, 1.35f, 0.08f))
        }

    private fun createSpectator(furColor: Color4): Entity =
        Entity().apply {
            addChild(capsule(0.46f, 0.14f, Palette.audienceShirt, Surface.SATIN).at(0f, 0.22f, 0f))
            addChild(sphere(0.19f, furColor, Surface.MATTE).at(0f, 0.55f, 0f))
            addChild(sphere(0.075f, furColor, Surface.MATTE).at(-0.13f, 0.68f, 0f))
            addChild(sphere(0.075f, furColor, Surface.MATTE).at(0.13f, 0.68f, 0f))
        }

    /** Lightweight but species-readable menu rig: jersey, head, ears, muzzle, tail and racket. */
    private fun createMenuPreviewCharacter(palette: CharacterPalette): MenuPreviewRig {
        val root = Entity()
        val torsoWidth = palette.bodyRadius * 1.9f
        val torsoDepth = palette.bodyRadius * 1.18f
        val armX = torsoWidth * 0.56f
        val armLength = palette.armLength + 0.02f
        val body =
            box(torsoWidth, palette.bodyHeight, torsoDepth, palette.primary, Surface.SATIN, TextureKind.JERSEY)
                .at(0f, 0.7f, 0f)
        val headY = 1.2f + (palette.headRadius - 0.28f)
        val head = sphere(palette.headRadius, palette.fur, Surface.SATIN).at(0f, headY, 0f)
        addAnimalFeatures(root, palette, headY, palette.headRadius - 0.015f, compact = true)
        val rightArm = capsule(armLength, palette.armRadius, palette.primary, Surface.SATIN).at(armX, 0.78f, 0f)
        rightArm.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, palette.shoulderAngle))
        val grip = armEndPosition(armX, armLength, palette.shoulderAngle)
        val racketRoot = Entity()
        val rim = torus(0.3f, 0.225f, palette.accent, Surface.GLOSS).at(0f, 0.57f, 0f)
        rim.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(90f, 0f, 0f))
        val handle = box(0.07f, 0.46f, 0.07f, Palette.handle, Surface.RUBBER).at(0f, 0.04f, 0f)
        racketRoot.addChild(rim)
        racketRoot.addChild(handle)
        racketRoot.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(grip.x, grip.y, 0.035f))
            setScaleVector(Vector3(palette.racketScale, palette.racketScale, palette.racketScale))
            setEulerAngles(EulerAngles(0f, 0f, -35f))
        }
        listOf(body, head, rightArm, racketRoot).forEach(root::addChild)
        return MenuPreviewRig(root, palette.figureScale)
    }

    private fun createCharacter(palette: CharacterPalette): CharacterRig {
        val root = Entity()
        val headY = 1.22f + (palette.headRadius - 0.28f)
        val faceZ = palette.headRadius - 0.015f
        val torsoDepth = palette.bodyRadius * 1.18f
        val torsoWidth = palette.bodyRadius * 1.9f
        val torsoFrontZ = torsoDepth * 0.5f + 0.012f
        val armX = torsoWidth * 0.56f
        val body =
            box(torsoWidth, palette.bodyHeight, torsoDepth, palette.primary, Surface.SATIN, TextureKind.JERSEY)
                .at(0f, 0.73f, 0f)
        val chestStripe = box(torsoWidth + 0.02f, 0.085f, 0.025f, palette.accent, Surface.GLOSS).at(0f, 0.82f, torsoFrontZ)
        val shorts = box(torsoWidth * 0.9f, 0.22f, torsoDepth * 1.08f, palette.trim, Surface.MATTE).at(0f, 0.4f, 0f)
        val head = sphere(palette.headRadius, palette.fur, Surface.SATIN).at(0f, headY, 0f)
        addAnimalFeatures(root, palette, headY, faceZ, compact = false)
        val emblem = torus(0.12f, 0.065f, palette.accent, Surface.EMISSIVE).at(0f, 0.78f, palette.bodyRadius + 0.015f)
        emblem.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0.74f, torsoFrontZ + 0.008f))
            setEulerAngles(EulerAngles(90f, 0f, 0f))
        }
        val leftArm = capsule(palette.armLength, palette.armRadius, palette.primary, Surface.SATIN).at(-armX, 0.78f, 0f)
        val rightArmLength = palette.armLength + 0.02f
        val rightArm = capsule(rightArmLength, palette.armRadius, palette.primary, Surface.SATIN).at(armX, 0.78f, 0f)
        leftArm.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, -palette.shoulderAngle))
        rightArm.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, palette.shoulderAngle))
        val leftHandPosition = armEndPosition(-armX, palette.armLength, -palette.shoulderAngle)
        val rightHandPosition = armEndPosition(armX, rightArmLength, palette.shoulderAngle)
        val leftHand =
            sphere(palette.armRadius * 1.04f, palette.fur, Surface.SATIN).at(
                leftHandPosition.x,
                leftHandPosition.y,
                0f,
            )
        val rightHand =
            sphere(palette.armRadius * 1.04f, palette.fur, Surface.SATIN).at(
                rightHandPosition.x,
                rightHandPosition.y,
                0f,
            )
        val footX = palette.footWidth * 0.72f
        val leftFoot = box(palette.footWidth, 0.13f, palette.footDepth, palette.trim, Surface.RUBBER).at(-footX, 0.18f, 0.03f)
        val rightFoot = box(palette.footWidth, 0.13f, palette.footDepth, palette.trim, Surface.RUBBER).at(footX, 0.18f, 0.03f)
        val racket = createRacket(palette.accent)
        racket.root.components[TransformComponent::class.java]?.apply {
            setPosition(
                Vector3(
                    rightHandPosition.x,
                    rightHandPosition.y,
                    0.04f,
                ),
            )
            setScaleVector(Vector3(palette.racketScale, palette.racketScale, palette.racketScale))
            setEulerAngles(EulerAngles(0f, 0f, -35f))
        }
        root.components[TransformComponent::class.java]?.setScaleVector(
            Vector3(palette.figureScale, palette.figureScale, palette.figureScale),
        )
        listOf(
            body,
            chestStripe,
            shorts,
            head,
            emblem,
            leftArm,
            rightArm,
            leftHand,
            rightHand,
            leftFoot,
            rightFoot,
            racket.root,
        ).forEach(root::addChild)
        return CharacterRig(root, racket.root, palette.figureScale)
    }

    private fun addAnimalFeatures(
        root: Entity,
        palette: CharacterPalette,
        headY: Float,
        faceZ: Float,
        compact: Boolean,
    ) {
        val faceSign = if (faceZ >= 0f) 1f else -1f
        val muzzleZ = faceZ + faceSign * 0.055f
        val muzzle = ellipsoid(0.17f, 0.11f, 0.1f, palette.muzzle, Surface.MATTE).at(0f, headY - 0.07f, muzzleZ)
        val nose = sphere(if (compact) 0.038f else 0.045f, Palette.faceDetail, Surface.GLOSS).at(0f, headY - 0.035f, muzzleZ + faceSign * 0.09f)
        root.addChild(muzzle)
        root.addChild(nose)

        if (!compact) {
            root.addChild(sphere(0.038f, Palette.faceDetail, Surface.GLOSS).at(-0.09f, headY + 0.055f, faceZ + faceSign * 0.018f))
            root.addChild(sphere(0.038f, Palette.faceDetail, Surface.GLOSS).at(0.09f, headY + 0.055f, faceZ + faceSign * 0.018f))
        }

        when (palette.species) {
            AnimalSpecies.RABBIT -> {
                val leftEar = capsule(0.47f, 0.075f, palette.fur, Surface.SATIN).at(-0.13f, headY + 0.35f, 0f)
                val rightEar = capsule(0.47f, 0.075f, palette.fur, Surface.SATIN).at(0.13f, headY + 0.35f, 0f)
                leftEar.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, -7f))
                rightEar.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, 7f))
                root.addChild(leftEar)
                root.addChild(rightEar)
            }
            AnimalSpecies.FOX -> {
                val leftEar = capsule(0.3f, 0.085f, palette.fur, Surface.SATIN).at(-0.17f, headY + 0.25f, 0f)
                val rightEar = capsule(0.3f, 0.085f, palette.fur, Surface.SATIN).at(0.17f, headY + 0.25f, 0f)
                leftEar.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, -24f))
                rightEar.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, 24f))
                root.addChild(leftEar)
                root.addChild(rightEar)
            }
            AnimalSpecies.BEAR, AnimalSpecies.TIGER -> {
                val earRadius = if (palette.species == AnimalSpecies.BEAR) 0.115f else 0.1f
                root.addChild(sphere(earRadius, palette.fur, Surface.SATIN).at(-0.19f, headY + 0.2f, 0f))
                root.addChild(sphere(earRadius, palette.fur, Surface.SATIN).at(0.19f, headY + 0.2f, 0f))
            }
        }

        val tailZ = -faceSign * (palette.bodyRadius + 0.1f)
        when (palette.species) {
            AnimalSpecies.RABBIT -> root.addChild(sphere(0.13f, Palette.cream, Surface.MATTE).at(-0.15f, 0.5f, tailZ))
            AnimalSpecies.BEAR -> root.addChild(sphere(0.11f, palette.fur, Surface.MATTE).at(-0.18f, 0.5f, tailZ))
            AnimalSpecies.TIGER, AnimalSpecies.FOX -> {
                val tailRadius = if (palette.species == AnimalSpecies.FOX) 0.12f else 0.075f
                val tail = capsule(if (palette.species == AnimalSpecies.FOX) 0.62f else 0.48f, tailRadius, palette.fur, Surface.SATIN)
                    .at(-0.25f, 0.48f, tailZ)
                tail.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(faceSign * 24f, 0f, 38f))
                root.addChild(tail)
            }
        }

        if (!compact && palette.species == AnimalSpecies.TIGER) {
            listOf(-0.13f, 0f, 0.13f).forEachIndexed { index, x ->
                val stripe = box(0.045f, 0.15f - index * 0.02f, 0.022f, palette.marking, Surface.MATTE)
                    .at(x, headY + 0.18f, faceZ + faceSign * 0.03f)
                stripe.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f, 0f, (index - 1) * 11f))
                root.addChild(stripe)
            }
        }
    }

    private fun createRacket(color: Color4): RacketRig {
        val root = Entity()
        val rim = torus(0.32f, 0.24f, color, Surface.GLOSS).at(0f, 0.62f, 0f)
        rim.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(90f, 0f, 0f))
        val handle = box(0.075f, 0.48f, 0.075f, Palette.handle, Surface.RUBBER).at(0f, 0.03f, 0f)
        val throat = box(0.1f, 0.25f, 0.07f, color, Surface.GLOSS).at(0f, 0.36f, 0f)
        val verticalString = box(0.018f, 0.47f, 0.018f, Palette.racketString, Surface.GLOSS).at(0f, 0.62f, 0f)
        val horizontalString = box(0.47f, 0.018f, 0.018f, Palette.racketString, Surface.GLOSS).at(0f, 0.62f, 0f)
        root.addChild(rim)
        root.addChild(handle)
        root.addChild(throat)
        root.addChild(verticalString)
        root.addChild(horizontalString)
        return RacketRig(root)
    }

    private fun armEndPosition(
        centerX: Float,
        armLength: Float,
        angleDegrees: Float,
    ): Vector3 {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val halfLength = armLength * 0.5f
        return Vector3(
            centerX + kotlin.math.sin(radians).toFloat() * halfLength,
            0.78f - kotlin.math.cos(radians).toFloat() * halfLength,
            0f,
        )
    }

    private fun box(
        width: Float,
        height: Float,
        depth: Float,
        color: Color4,
        surface: Surface = Surface.MATTE,
        texture: TextureKind = TextureKind.NONE,
    ): ModelEntity =
        ModelEntity(
            mesh(MeshKey(Primitive.BOX)) {
                MeshResource.createBox(Vector3(1f, 1f, 1f), 0.035f)
            },
            material(color, surface, texture),
        ).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(width, height, depth))
        }

    private fun sphere(
        radius: Float,
        color: Color4,
        surface: Surface = Surface.MATTE,
    ): ModelEntity =
        ModelEntity(
            mesh(MeshKey(Primitive.SPHERE)) { MeshResource.createSphere(1f) },
            material(color, surface),
        ).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(radius, radius, radius))
        }

    private fun ellipsoid(
        radiusX: Float,
        radiusY: Float,
        radiusZ: Float,
        color: Color4,
        surface: Surface = Surface.MATTE,
    ): ModelEntity =
        ModelEntity(
            mesh(MeshKey(Primitive.SPHERE)) { MeshResource.createSphere(1f) },
            material(color, surface),
        ).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(radiusX, radiusY, radiusZ))
        }

    private fun capsule(
        height: Float,
        radius: Float,
        color: Color4,
        surface: Surface = Surface.MATTE,
    ): ModelEntity =
        ModelEntity(
            mesh(MeshKey(Primitive.CAPSULE)) { MeshResource.createCapsule(1f, 0.5f) },
            material(color, surface),
        ).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(radius * 2f, height, radius * 2f))
        }

    private fun cylinder(
        height: Float,
        radius: Float,
        color: Color4,
        surface: Surface = Surface.MATTE,
    ): ModelEntity =
        ModelEntity(
            mesh(MeshKey(Primitive.CYLINDER)) { MeshResource.createCylinder(1f, 0.5f) },
            material(color, surface),
        ).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(radius * 2f, height, radius * 2f))
        }

    private fun torus(
        outerRadius: Float,
        innerRadius: Float,
        color: Color4,
        surface: Surface = Surface.MATTE,
    ): ModelEntity {
        val requestedRatio = innerRadius / outerRadius
        val canonicalRatio =
            when {
                requestedRatio <= 0.65f -> 0.55f
                requestedRatio <= 0.78f -> 0.75f
                else -> 0.82f
            }
        return ModelEntity(
            mesh(MeshKey(Primitive.TORUS, canonicalRatio)) {
                MeshResource.createTorus(1f, canonicalRatio)
            },
            material(color, surface),
        ).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(outerRadius, outerRadius, outerRadius))
        }
    }

    private fun mesh(
        key: MeshKey,
        create: () -> MeshResource,
    ): MeshResource =
        meshes.getOrPut(key) {
            create().apply { toGlobal() }
        }

    private fun material(
        color: Color4,
        surface: Surface,
        texture: TextureKind = TextureKind.NONE,
    ): PhysicallyBasedMaterial =
        materials.getOrPut(MaterialKey(color, surface, texture)) {
            PhysicallyBasedMaterial.create(surface.blendingMode).apply {
                setBaseColor(color)
                textures[texture]?.let(::setBaseColorTexture)
                setRoughness(surface.roughness)
                setMetallic(surface.metallic)
                setAmbientOcclusion(0.92f)
                setOpacity(surface.opacity)
                if (surface.emissive) setEmissiveColor(color)
                toGlobal()
            }
        }

    private fun loadTexture(path: String): TextureResource? =
        runCatching {
            TextureResource.load(path, LoadType.FROM_ASSETS).apply { toGlobal() }
        }.getOrNull()

    private fun ModelEntity.at(
        x: Float,
        y: Float,
        z: Float,
    ): ModelEntity = apply {
        components[TransformComponent::class.java]?.setPosition(Vector3(x, y, z))
    }

    private fun setPosition(
        entity: Entity,
        x: Float,
        y: Float,
        z: Float,
    ) {
        entity.components[TransformComponent::class.java]?.setPosition(Vector3(x, y, z))
    }

    private fun swingAngle(timer: Float): Float {
        if (timer <= 0f) return 0f
        val progress = 1f - (timer / 0.28f).coerceIn(0f, 1f)
        return kotlin.math.sin(progress * Math.PI).toFloat() * 110f
    }

    private fun updateTrail(state: TennisGameState) {
        if (state.ball.flight == null) {
            clearTrail()
            return
        }
        if (state.rallyCount != lastTrailRallyCount) {
            clearTrail()
            lastTrailRallyCount = state.rallyCount
        }
        val current = Vector3(state.ball.position.x, state.ball.position.y, sceneZ(state.ball.position.z))
        val previous = lastTrailPosition
        if (previous == null || Vector3.distance(previous, current) >= TRAIL_SAMPLE_DISTANCE) {
            trailCursor = (trailCursor + 1) % TRAIL_COUNT
            trailHistory[trailCursor] = current
            trailSize = minOf(TRAIL_COUNT, trailSize + 1)
            lastTrailPosition = current
        }
        trailGhosts.forEachIndexed { index, ghost ->
            val transform = ghost.components[TransformComponent::class.java] ?: return@forEachIndexed
            if (index >= trailSize) {
                transform.setScaleVector(Vector3.ZERO)
            } else {
                val historyIndex = (trailCursor - index + TRAIL_COUNT) % TRAIL_COUNT
                val point = trailHistory[historyIndex] ?: return@forEachIndexed
                val weight = (trailSize - index).toFloat() / trailSize
                val skillBoost = if (state.ball.flight.isSkillShot) 1.55f else 1f
                val scale = TRAIL_GHOST_RADIUS * (0.35f + weight * 0.55f) * skillBoost
                transform.setPosition(point)
                transform.setScaleVector(Vector3(scale, scale, scale))
            }
        }
    }

    private fun clearTrail() {
        trailCursor = -1
        trailSize = 0
        lastTrailPosition = null
        trailGhosts.forEach {
            it.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        }
    }

    private fun updateImpactAndSkillEffects(
        state: TennisGameState,
        visualSeconds: Double,
    ) {
        if (state.rallyCount != lastRallyCount && state.ball.flight != null) {
            lastRallyCount = state.rallyCount
            impactStartedNanos = System.nanoTime()
            impactRing.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(state.ball.position.x, state.ball.position.y, sceneZ(state.ball.position.z)))
                setScaleVector(Vector3(0.3f, 0.3f, 0.3f))
            }
        }
        val impactAge = (System.nanoTime() - impactStartedNanos) / 1_000_000_000f
        impactRing.components[TransformComponent::class.java]?.apply {
            if (impactStartedNanos == 0L || impactAge >= IMPACT_DURATION_SECONDS) {
                setScaleVector(Vector3.ZERO)
            } else {
                val progress = impactAge / IMPACT_DURATION_SECONDS
                val scale = IMPACT_RING_RADIUS * (0.35f + progress * 1.8f)
                setScaleVector(Vector3(scale, scale, scale))
                setEulerAngles(EulerAngles(0f, 0f, progress * 90f))
            }
        }

        skillReadyRing.components[TransformComponent::class.java]?.apply {
            if (state.skillEnergy < 100f) {
                setScaleVector(Vector3.ZERO)
            } else {
                val pulse = SKILL_RING_RADIUS * (1f + kotlin.math.sin(visualSeconds * 5.5).toFloat() * 0.1f)
                setPosition(Vector3(state.player.position.x, 0.15f, sceneZ(state.player.position.z)))
                setScaleVector(Vector3(pulse, pulse, pulse))
                setEulerAngles(EulerAngles(90f, 0f, (visualSeconds * 55.0).toFloat() % 360f))
            }
        }
    }

    private fun sceneZ(simulationZ: Float): Float = -simulationZ

    private data class CharacterRig(
        val root: Entity,
        val racket: Entity,
        val figureScale: Float,
    ) {
        fun uniformScale(multiplier: Float = 1f): Vector3 {
            val scale = figureScale * multiplier
            return Vector3(scale, scale, scale)
        }
    }

    private data class MenuPreviewRig(
        val root: Entity,
        val figureScale: Float,
    ) {
        fun uniformScale(multiplier: Float = 1f): Vector3 {
            val scale = figureScale * multiplier
            return Vector3(scale, scale, scale)
        }
    }

    private data class RacketRig(
        val root: Entity,
    )

    private data class MaterialKey(
        val color: Color4,
        val surface: Surface,
        val texture: TextureKind,
    )

    private data class MeshKey(
        val primitive: Primitive,
        val variant: Float = 0f,
    )

    private enum class Primitive {
        BOX,
        SPHERE,
        CAPSULE,
        CYLINDER,
        TORUS,
    }

    private enum class Surface(
        val roughness: Float,
        val metallic: Float,
        val emissive: Boolean = false,
        val blendingMode: BlendingMode = BlendingMode.OPAQUE,
        val opacity: Float = 1f,
    ) {
        MATTE(0.86f, 0.01f),
        SATIN(0.48f, 0.04f),
        GLOSS(0.2f, 0.12f),
        METAL(0.18f, 0.78f),
        RUBBER(0.96f, 0f),
        EMISSIVE(0.28f, 0.05f, true),
        NET(0.72f, 0.02f, blendingMode = BlendingMode.TRANSPARENT, opacity = 0.58f),
    }

    private enum class TextureKind {
        NONE,
        COURT,
        JERSEY,
    }

    private enum class AnimalSpecies {
        TIGER,
        RABBIT,
        BEAR,
        FOX,
    }

    private data class CharacterPalette(
        val species: AnimalSpecies,
        val primary: Color4,
        val secondary: Color4,
        val accent: Color4,
        val trim: Color4,
        val fur: Color4,
        val muzzle: Color4,
        val marking: Color4,
        val bodyHeight: Float,
        val bodyRadius: Float,
        val headRadius: Float,
        val armLength: Float,
        val armRadius: Float,
        val shoulderAngle: Float,
        val footWidth: Float,
        val footDepth: Float,
        val racketScale: Float,
        val figureScale: Float,
    ) {
        companion object {
            val player =
                CharacterPalette(
                    AnimalSpecies.TIGER,
                    Palette.tigerJersey,
                    Palette.cream,
                    Palette.honey,
                    Palette.forest,
                    Palette.tigerFur,
                    Palette.tigerMuzzle,
                    Palette.tigerStripe,
                    bodyHeight = 0.6f,
                    bodyRadius = 0.31f,
                    headRadius = 0.28f,
                    armLength = 0.37f,
                    armRadius = 0.08f,
                    shoulderAngle = 25f,
                    footWidth = 0.25f,
                    footDepth = 0.36f,
                    racketScale = 1f,
                    figureScale = 1.08f,
                )

            fun forAi(archetype: AiArchetype): CharacterPalette =
                when (archetype) {
                    AiArchetype.SPEEDSTER ->
                        CharacterPalette(
                            AnimalSpecies.RABBIT,
                            Palette.rabbitJersey,
                            Palette.cream,
                            Palette.rabbitAccent,
                            Palette.mintDark,
                            Palette.rabbitFur,
                            Palette.rabbitMuzzle,
                            Palette.rabbitAccent,
                            bodyHeight = 0.64f,
                            bodyRadius = 0.25f,
                            headRadius = 0.27f,
                            armLength = 0.43f,
                            armRadius = 0.06f,
                            shoulderAngle = 18f,
                            footWidth = 0.2f,
                            footDepth = 0.3f,
                            racketScale = 0.92f,
                            figureScale = 1.06f,
                        )
                    AiArchetype.POWERHOUSE ->
                        CharacterPalette(
                            AnimalSpecies.BEAR,
                            Palette.bearJersey,
                            Palette.cream,
                            Palette.honey,
                            Palette.woodDark,
                            Palette.bearFur,
                            Palette.bearMuzzle,
                            Palette.woodDark,
                            bodyHeight = 0.58f,
                            bodyRadius = 0.38f,
                            headRadius = 0.32f,
                            armLength = 0.35f,
                            armRadius = 0.11f,
                            shoulderAngle = 38f,
                            footWidth = 0.3f,
                            footDepth = 0.4f,
                            racketScale = 1.14f,
                            figureScale = 1.08f,
                        )
                    AiArchetype.TRICKSTER ->
                        CharacterPalette(
                            AnimalSpecies.FOX,
                            Palette.foxJersey,
                            Palette.cream,
                            Palette.foxAccent,
                            Palette.berryDark,
                            Palette.foxFur,
                            Palette.foxMuzzle,
                            Palette.foxAccent,
                            bodyHeight = 0.6f,
                            bodyRadius = 0.27f,
                            headRadius = 0.29f,
                            armLength = 0.4f,
                            armRadius = 0.07f,
                            shoulderAngle = 30f,
                            footWidth = 0.23f,
                            footDepth = 0.34f,
                            racketScale = 1f,
                            figureScale = 1.07f,
                        )
                }
        }
    }

    private object Palette {
        val grass = rgb(0x69, 0xA8, 0x63)
        val leafDark = rgb(0x3F, 0x7C, 0x51)
        val leafLight = rgb(0x7F, 0xBB, 0x69)
        val islandSoil = rgb(0xA9, 0x73, 0x4F)
        val clay = rgb(0xE8, 0x79, 0x55)
        val woodLight = rgb(0xD9, 0xA2, 0x63)
        val woodDark = rgb(0x73, 0x4A, 0x34)
        val cream = rgb(0xFF, 0xF2, 0xD3)
        val forest = rgb(0x36, 0x68, 0x4A)
        val sky = rgb(0x76, 0xC7, 0xDE)
        val mint = rgb(0x7B, 0xD5, 0xA4)
        val mintDark = rgb(0x32, 0x7F, 0x67)
        val coral = rgb(0xF0, 0x78, 0x66)
        val honey = rgb(0xF5, 0xC5, 0x56)
        val gold = honey
        val energy = honey
        val ball = rgb(0xD5, 0xF2, 0x56)
        val white = rgb(0xFF, 0xFF, 0xFF)
        val net = rgba(0x2E, 0x4D, 0x3A, 0xA8)
        val faceDetail = rgb(0x2A, 0x26, 0x24)
        val handle = rgb(0x3C, 0x36, 0x31)
        val racketString = rgb(0xF8, 0xEC, 0xD2)
        val audienceShirt = rgb(0xE7, 0xB5, 0x70)

        val tigerJersey = rgb(0x2D, 0x8A, 0x69)
        val tigerFur = rgb(0xED, 0x8C, 0x3E)
        val tigerMuzzle = rgb(0xFF, 0xDA, 0xA3)
        val tigerStripe = rgb(0x4B, 0x2A, 0x21)

        val rabbitJersey = rgb(0x52, 0xA9, 0xBA)
        val rabbitAccent = rgb(0xF5, 0x9A, 0xAD)
        val rabbitFur = rgb(0xF6, 0xE8, 0xD7)
        val rabbitMuzzle = rgb(0xFF, 0xF2, 0xE6)

        val bearJersey = rgb(0xD8, 0x66, 0x4D)
        val bearFur = rgb(0x74, 0x4B, 0x34)
        val bearMuzzle = rgb(0xC8, 0x91, 0x62)

        val foxJersey = rgb(0x75, 0x68, 0xB7)
        val foxAccent = rgb(0xAC, 0x91, 0xDD)
        val berryDark = rgb(0x4D, 0x3C, 0x72)
        val foxFur = rgb(0xD9, 0x63, 0x36)
        val foxMuzzle = rgb(0xFF, 0xE0, 0xB9)

        private fun rgb(r: Int, g: Int, b: Int) = rgba(r, g, b, 0xFF)

        private fun rgba(r: Int, g: Int, b: Int, a: Int) =
            Color4(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    private companion object {
        const val ROSTER_YAW = 0f
        const val WORLD_SCALE = 0.2f
        const val BALL_RADIUS = 0.14f
        const val LANDING_RING_RADIUS = 0.48f
        const val TRAIL_GHOST_RADIUS = 0.115f
        const val SKILL_RING_RADIUS = 0.62f
        const val IMPACT_RING_RADIUS = 0.42f
        const val MENU_AI_START_X = 0.55f
        const val MENU_AI_SPACING_X = 1.35f
        const val MENU_AI_SELECTED_SCALE = 1.2f
        const val MENU_AI_UNSELECTED_SCALE = 0.98f
        const val TRAIL_COUNT = 5
        const val TRAIL_SAMPLE_DISTANCE = 0.16f
        const val IMPACT_DURATION_SECONDS = 0.34f
    }
}
