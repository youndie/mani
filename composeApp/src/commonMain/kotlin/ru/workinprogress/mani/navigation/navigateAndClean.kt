package ru.workinprogress.mani.navigation

import androidx.navigation.NavController

fun NavController.navigateAndClean(route: String) {
    navigate(route = route) {
        popUpTo(graph.startDestinationRoute!!) { inclusive = true }
    }
    graph.setStartDestination(route)
}
