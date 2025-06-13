package com.nova.music.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nova.music.data.model.Song

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromSongList(value: List<Song>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toSongList(value: String): List<Song> {
        val listType = object : TypeToken<List<Song>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
} 