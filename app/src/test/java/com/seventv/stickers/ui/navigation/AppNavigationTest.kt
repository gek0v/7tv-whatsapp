package com.seventv.stickers.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {

    @Test
    fun screenRoutesAndTitlesMatchDesignSpecification() {
        assertEquals("explore", Screen.Explore.route)
        assertEquals("Explorar", Screen.Explore.title)

        assertEquals("channel", Screen.Channel.route)
        assertEquals("Canales", Screen.Channel.title)

        assertEquals("packs", Screen.Packs.route)
        assertEquals("Mis Packs", Screen.Packs.title)

        assertEquals("pack_detail/{packId}", Screen.PackDetail.route)
        assertEquals("Detalle", Screen.PackDetail.title)
        assertEquals("pack_detail/custom_123", Screen.PackDetail.createRoute("custom_123"))
    }

    @Test
    fun bottomNavItemsContainExpectedScreens() {
        assertEquals(3, bottomNavItems.size)
        assertTrue(bottomNavItems.contains(Screen.Explore))
        assertTrue(bottomNavItems.contains(Screen.Channel))
        assertTrue(bottomNavItems.contains(Screen.Packs))
    }

    @Test
    fun bottomNavItemsHaveUniqueRoutesAndTitles() {
        val routes = bottomNavItems.map { it.route }
        val titles = bottomNavItems.map { it.title }
        assertEquals(routes.distinct().size, bottomNavItems.size)
        assertEquals(titles.distinct().size, bottomNavItems.size)
    }

    @Test
    fun packDetailRouteMatchesParam() {
        val route = Screen.PackDetail.createRoute("custom_pack_42")
        assertEquals("pack_detail/custom_pack_42", route)
    }
}
