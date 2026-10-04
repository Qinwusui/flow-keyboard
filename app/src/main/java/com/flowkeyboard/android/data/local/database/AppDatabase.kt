package com.flowkeyboard.android.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.SQLiteConnection

@Database(entities = [DictionaryEntity::class, TypingStatEntity::class, UserTransitionEntity::class, BigramTransitionEntity::class], version = 7, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun statDao(): StatDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, "flow_keyboard.db",
        ).fallbackToDestructiveMigration().addCallback(seedCallback(context.applicationContext)).build()

        // onCreate runs inside Room's creation transaction: queries never see a partly seeded DB.
        fun seedCallback(context: Context) = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val insert = db.compileStatement("INSERT OR IGNORE INTO dictionary (word,pinyin,initials,baseWeight,frequency) VALUES (?,?,?,?,0)")
                insert.use { statement ->
                    context.assets.open("dictionary.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                            val columns = line.split('\t')
                            require(columns.size == 4) { "Invalid dictionary seed row" }
                            statement.bindString(1, columns[0])
                            statement.bindString(2, columns[1])
                            statement.bindString(3, columns[2])
                            statement.bindLong(4, columns[3].toLong())
                            statement.executeInsert()
                            statement.clearBindings()
                        }
                    }
                }
                try {
                    val insertTrans = db.compileStatement("INSERT OR IGNORE INTO bigram_transitions (fromWord,toWord,weight) VALUES (?,?,?)")
                    insertTrans.use { statement ->
                        context.assets.open("transitions.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                                val columns = line.split('\t')
                                if (columns.size == 3) {
                                    statement.bindString(1, columns[0])
                                    statement.bindString(2, columns[1])
                                    statement.bindLong(3, columns[2].toLong())
                                    statement.executeInsert()
                                    statement.clearBindings()
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onCreate(connection: SQLiteConnection) {
                connection.prepare("INSERT OR IGNORE INTO dictionary (word,pinyin,initials,baseWeight,frequency) VALUES (?,?,?,?,0)").use { statement ->
                    context.assets.open("dictionary.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                            val columns = line.split('\t')
                            require(columns.size == 4) { "Invalid dictionary seed row" }
                            statement.bindText(1, columns[0])
                            statement.bindText(2, columns[1])
                            statement.bindText(3, columns[2])
                            statement.bindLong(4, columns[3].toLong())
                            statement.step()
                            statement.reset()
                            statement.clearBindings()
                        }
                    }
                }
                try {
                    connection.prepare("INSERT OR IGNORE INTO bigram_transitions (fromWord,toWord,weight) VALUES (?,?,?)").use { statement ->
                        context.assets.open("transitions.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                                val columns = line.split('\t')
                                if (columns.size == 3) {
                                    statement.bindText(1, columns[0])
                                    statement.bindText(2, columns[1])
                                    statement.bindLong(3, columns[2].toLong())
                                    statement.step()
                                    statement.reset()
                                    statement.clearBindings()
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
