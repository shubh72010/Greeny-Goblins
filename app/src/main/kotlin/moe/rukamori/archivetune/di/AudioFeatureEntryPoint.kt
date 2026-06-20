package moe.rukamori.archivetune.di

import moe.rukamori.archivetune.taster.AudioFeatureCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioFeatureEntryPoint {
    fun audioFeatureCache(): AudioFeatureCache
}
