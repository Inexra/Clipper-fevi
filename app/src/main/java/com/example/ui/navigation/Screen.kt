package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    
    data class ImportVideo(val workflow: String = "clipper") : Screen("import_video/$workflow") {
        companion object {
            const val ROUTE = "import_video/{workflow}"
            fun createRoute(workflow: String) = "import_video/$workflow"
        }
    }

    data class ClippingConfig(val projectId: Long) : Screen("clipping_config/$projectId") {
        companion object {
            const val ROUTE = "clipping_config/{projectId}"
            fun createRoute(projectId: Long) = "clipping_config/$projectId"
        }
    }

    data class CustomLengthConfig(val projectId: Long) : Screen("custom_length_config/$projectId") {
        companion object {
            const val ROUTE = "custom_length_config/{projectId}"
            fun createRoute(projectId: Long) = "custom_length_config/$projectId"
        }
    }

    data class Processing(val projectId: Long, val workflow: String) : Screen("processing/$projectId/$workflow") {
        companion object {
            const val ROUTE = "processing/{projectId}/{workflow}"
            fun createRoute(projectId: Long, workflow: String) = "processing/$projectId/$workflow"
        }
    }

    data class ClipsResult(val projectId: Long) : Screen("clips_result/$projectId") {
        companion object {
            const val ROUTE = "clips_result/{projectId}"
            fun createRoute(projectId: Long) = "clips_result/$projectId"
        }
    }

    data class ClipDetail(val clipId: String) : Screen("clip_detail/$clipId") {
        companion object {
            const val ROUTE = "clip_detail/{clipId}"
            fun createRoute(clipId: String) = "clip_detail/$clipId"
        }
    }
}
