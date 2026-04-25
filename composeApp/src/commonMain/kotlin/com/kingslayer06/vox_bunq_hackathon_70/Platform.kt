package com.kingslayer06.vox_bunq_hackathon_70

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform