package com.simplemobiletools.musicplayer.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.MediaStore
import android.text.TextUtils
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song

class DBHelper private constructor(val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val TABLE_NAME_PLAYLISTS = "playlists"
    private val COL_ID = "id"
    private val COL_TITLE = "title"

    private val TABLE_NAME_SONGS = "songs"
    private val COL_PATH = "path"
    private val COL_PLAYLIST_ID = "playlist_id"

    private val mDb: SQLiteDatabase = writableDatabase

    companion object {
        private val DB_VERSION = 1
        val DB_NAME = "playlists.db"
        val INITIAL_PLAYLIST_ID = 1

        fun newInstance(context: Context): DBHelper {
            return DBHelper(context)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_PLAYLISTS ($COL_ID INTEGER PRIMARY KEY, $COL_TITLE TEXT)")
        createSongsTable(db)
        addInitialPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    private fun createSongsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_SONGS ($COL_ID INTEGER PRIMARY KEY, $COL_PATH TEXT, $COL_PLAYLIST_ID INTEGER, " +
                "UNIQUE($COL_PATH, $COL_PLAYLIST_ID) ON CONFLICT IGNORE)")
    }

    private fun addInitialPlaylist(db: SQLiteDatabase) {
        val initialPlaylist = context.resources.getString(R.string.initial_playlist)
        val playlist = Playlist(INITIAL_PLAYLIST_ID, initialPlaylist)
        addPlaylist(playlist, db)
    }

    private fun addPlaylist(playlist: Playlist, db: SQLiteDatabase) {
        insertPlaylist(playlist, db)
    }

    fun insertPlaylist(playlist: Playlist, db: SQLiteDatabase = mDb): Int {
        val values = ContentValues().apply { put(COL_TITLE, playlist.title) }
        val insertedId = db.insert(TABLE_NAME_PLAYLISTS, null, values).toInt()
        return insertedId
    }

    fun addSongToPlaylist(path: String) {
        addSongsToPlaylist(ArrayList<String>().apply { add(path) })
    }

    fun addSongsToPlaylist(paths: ArrayList<String>) {
        val playlistId = context.config.currentPlaylist
        for (path in paths) {
            ContentValues().apply {
                put(COL_PATH, path)
                put(COL_PLAYLIST_ID, playlistId)
                mDb.insert(TABLE_NAME_SONGS, null, this)
            }
        }
    }

    private fun getCurrentPlaylistSongPaths(): ArrayList<String> {
        val paths = ArrayList<String>()
        val cols = arrayOf(COL_PATH)
        val selection = "$COL_PLAYLIST_ID = ?"
        val selectionArgs = arrayOf(context.config.currentPlaylist.toString())
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_SONGS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val path = cursor.getStringValue(COL_PATH)
                    paths.add(path)
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        return paths
    }

    fun getSongs(): ArrayList<Song> {
        val songPaths = getCurrentPlaylistSongPaths()
        val songs = ArrayList<Song>(songPaths.size)
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA)

        val args = "\"" + TextUtils.join("\",\"", songPaths) + "\""
        val selection = "${MediaStore.Audio.Media.DATA} IN ($args)"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, columns, selection, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val id = cursor.getLongValue(MediaStore.Audio.Media._ID)
                    val title = cursor.getStringValue(MediaStore.Audio.Media.TITLE)
                    val artist = cursor.getStringValue(MediaStore.Audio.Media.ARTIST)
                    val path = cursor.getStringValue(MediaStore.Audio.Media.DATA)
                    val duration = cursor.getIntValue(MediaStore.Audio.Media.DURATION) / 1000
                    val song = Song(id, title, artist, path, duration)
                    songs.add(song)
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        return songs
    }
}