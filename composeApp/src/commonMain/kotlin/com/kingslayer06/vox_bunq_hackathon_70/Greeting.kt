package com.kingslayer06.vox_bunq_hackathon_70

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}