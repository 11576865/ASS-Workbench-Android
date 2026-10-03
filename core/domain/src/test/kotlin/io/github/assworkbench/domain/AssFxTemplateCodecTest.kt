package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssFxTemplateCodecTest {
    @Test
    fun roundTripsNamedTemplateWithOptionalLayers() {
        val template = AssFxTemplate(
            name = "镜像 OP · soft",
            reflection = AssReflectionFxSpec(
                offsetY = 72.5,
                verticalScalePercent = 31.0,
                opacityPercent = 42.0,
                blur = 1.2,
            ),
            glow = AssGlowFxSpec(
                opacityPercent = 18.0,
                blur = 4.5,
                border = 2.25,
            ),
            entrance = AssFlipEntranceSpec(
                durationMs = 360,
                startScalePercent = 6.0,
                overshootScalePercent = 122.0,
                startRotationXDegrees = 92.0,
                accel = 1.15,
            ),
        )

        val decoded = AssFxTemplateCodec.decode(AssFxTemplateCodec.encode(template))
        assertEquals(template, decoded)
    }

    @Test
    fun roundTripsDisabledOptionalLayers() {
        val template = AssFxTemplate(
            name = "Reflection only",
            glow = null,
            entrance = null,
        )
        val decoded = AssFxTemplateCodec.decode(AssFxTemplateCodec.encode(template))
        assertNull(decoded.glow)
        assertNull(decoded.entrance)
        assertEquals(template.reflection, decoded.reflection)
    }

    @Test
    fun rejectsUnknownOrInvalidPayloadInsteadOfGuessing() {
        assertFailsWith<IllegalArgumentException> {
            AssFxTemplateCodec.decode("NOT_A_TEMPLATE\nname64=QQ")
        }
        assertFailsWith<IllegalArgumentException> {
            AssFxTemplateCodec.encode(AssFxTemplate(name = "", glow = null, entrance = null))
        }
    }
}
