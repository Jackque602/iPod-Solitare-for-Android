package com.jackque.solitaire.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-master pin of the deal produced by seed 1. The shuffle is promised
 * to be stable forever (players share seeds to replay deals), so if this
 * test ever fails the PRNG or deal order changed incompatibly - fix the
 * regression, do not update the expectation.
 */
class GoldenDealTest {

    @Test
    fun `seed 1 always deals the exact same game`() {
        val s = Dealer.deal(1L, DrawMode.DRAW_ONE)
        val expectedTableau = listOf(
            "TD",
            "9s AD",
            "3c 8d JH",
            "5s 5c Ts 4D",
            "3s 5h 6c 4h QD",
            "9d 2h 8s Kh 9h AC",
            "7s Qs 8c 6s Js Qh 2C",
        )
        assertEquals(expectedTableau, s.tableau.map { pile -> pile.joinToString(" ") { it.code() } })
        assertEquals(
            "Tc 4c 7c Kd 3d 2d Jd 2s As Qc 9c 6d 7d Kc Jc 3h Ah 5d 7h Ks 4s 6h Th 8h",
            s.stock.joinToString(" ") { it.code() },
        )
    }
}
