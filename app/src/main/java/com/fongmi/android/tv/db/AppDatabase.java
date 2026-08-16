package com.fongmi.android.tv.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Favorite;
import com.fongmi.android.tv.bean.FlagScore;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.DownloadDao;
import com.fongmi.android.tv.db.dao.FavoriteDao;
import com.fongmi.android.tv.db.dao.FlagScoreDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.orhanobut.logger.Logger;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class, Download.class, FlagScore.class, Favorite.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 40;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";
    public static final String BACKUP_SUFFIX = "bk.gz";

    private static volatile AppDatabase instance;
    private static volatile boolean backingUp;
    private static volatile boolean restoring;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    public static synchronized void reset() {
        if (instance != null) instance.close();
        instance = null;
    }

    public static void backup() {
        backup(new com.fongmi.android.tv.impl.Callback());
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback) {
        android.util.Log.d("Backup", "backup() called - Setting.getBackupMode(): " + Setting.getBackupMode());
        if (backingUp || restoring) {
            Logger.t(NAME).w("backup skipped: backingUp=" + backingUp + " restoring=" + restoring);
            return;
        }
        backingUp = true;
        Task.execute(() -> {
            try {
                File file = new File(Path.tv(), "tv-" + LocalDate.now().format(Formatters.DATE) + ".bk");
                Backup backup = Backup.create();
                if (backup.isEmpty()) {
                    Logger.t(NAME).w("backup FAILED: backup is empty");
                    App.post(callback::error);
                } else {
                    Path.write(file, backup.toString().getBytes());
                    File gz = FileUtil.gzipCompress(file);
                    App.post(() -> callback.success(gz.getAbsolutePath()));
                    cleanOld();
                }
            } catch (Exception e) {
                Logger.e(e, "Backup failed");
                App.post(callback::error);
            } finally {
                backingUp = false;
            }
        });
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback) {
        if (backingUp || restoring) {
            android.util.Log.w("Backup", "restore skipped: backingUp=" + backingUp + " restoring=" + restoring);
            return;
        }
        restoring = true;
        android.util.Log.d("Backup", "restore START: " + file.getAbsolutePath());
        Task.execute(() -> {
            try {
                String content;
                if (file.getName().endsWith(".gz")) {
                    File restore = Path.cache("restore");
                    if (FileUtil.gzipDecompress(file, restore)) {
                        content = Path.read(restore);
                        Path.clear(restore);
                    } else {
                        android.util.Log.e("Backup", "restore FAILED: decompress error");
                        content = "";
                    }
                } else {
                    content = Path.read(file);
                }
                android.util.Log.d("Backup", "restore content length: " + content.length());
                Backup backup = Backup.objectFrom(content);
                if (backup.isEmpty()) {
                    android.util.Log.w("Backup", "restore FAILED: backup is empty");
                    App.post(callback::error);
                } else {
                    backup.restore();
                    android.util.Log.d("Backup", "restore SUCCESS");
                    App.post(callback::success);
                }
            } catch (Exception e) {
                android.util.Log.e("Backup", "restore FAILED", e);
                App.post(callback::error);
            } finally {
                restoring = false;
            }
        });
    }

    private static void cleanOld() {
        List<File> items = new ArrayList<>();
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) items.add(file);
        if (!items.isEmpty()) items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        if (items.size() > 7) for (int i = 7; i < items.size(); i++) Path.clear(items.get(i));
    }
    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(wrap(MIGRATION_11_12))
                .addMigrations(wrap(MIGRATION_12_13))
                .addMigrations(wrap(MIGRATION_13_14))
                .addMigrations(wrap(MIGRATION_14_15))
                .addMigrations(wrap(MIGRATION_15_16))
                .addMigrations(wrap(MIGRATION_16_17))
                .addMigrations(wrap(MIGRATION_17_18))
                .addMigrations(wrap(MIGRATION_18_19))
                .addMigrations(wrap(MIGRATION_19_20))
                .addMigrations(wrap(MIGRATION_20_21))
                .addMigrations(wrap(MIGRATION_21_22))
                .addMigrations(wrap(MIGRATION_22_23))
                .addMigrations(wrap(MIGRATION_23_24))
                .addMigrations(wrap(MIGRATION_24_25))
                .addMigrations(wrap(MIGRATION_25_26))
                .addMigrations(wrap(MIGRATION_26_27))
                .addMigrations(wrap(MIGRATION_27_28))
                .addMigrations(wrap(MIGRATION_28_29))
                .addMigrations(wrap(MIGRATION_29_30))
                .addMigrations(wrap(MIGRATION_30_31))
                .addMigrations(wrap(MIGRATION_31_32))
                .addMigrations(wrap(MIGRATION_32_33))
                .addMigrations(wrap(MIGRATION_33_34))
                .addMigrations(wrap(MIGRATION_34_35))
                .addMigrations(wrap(MIGRATION_35_36))
                .addMigrations(wrap(MIGRATION_36_37))
                .addMigrations(wrap(MIGRATION_37_38))
                .addMigrations(wrap(MIGRATION_38_39))
                .addMigrations(wrap(MIGRATION_39_40))
                .allowMainThreadQueries().fallbackToDestructiveMigration().build();
    }

    private static Migration wrap(Migration migration) {
        return new Migration(migration.startVersion, migration.endVersion) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase database) {
                App.post(() -> Notify.show(ResUtil.getString(R.string.db_upgrading, startVersion, endVersion)));
                migration.migrate(database);
            }
        };
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();

    public abstract DownloadDao getDownloadDao();

    public abstract FlagScoreDao getFlagScoreDao();

    public abstract FavoriteDao getFavoriteDao();

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN type INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE Config ADD COLUMN home TEXT DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Keep ADD COLUMN type INTEGER DEFAULT 0 NOT NULL");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS index_Config_url");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Config_url_type ON Config(url, type)");
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN scale INTEGER DEFAULT -1 NOT NULL");
        }
    };

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN speed REAL DEFAULT 1 NOT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN player INTEGER DEFAULT -1 NOT NULL");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Track` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `player` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_player_type` ON `Track` (`key`, `player`, `type`)");
        }
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN parse TEXT DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN changeable INTEGER DEFAULT 1");
        }
    };

    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN name TEXT DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Device` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `uuid` TEXT, `name` TEXT, `ip` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Device_uuid_name` ON `Device` (`uuid`, `name`)");
        }
    };

    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Device ADD COLUMN type INTEGER DEFAULT 0 NOT NULL");
        }
    };

    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("UPDATE History SET player = 2 WHERE player = 0");
            if (Setting.getLivePlayer() == 0) Setting.putLivePlayer(2);
            if (Setting.getPlayer() == 0) Setting.putPlayer(2);
        }
    };

    static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Track ADD COLUMN `adaptive` INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN recordable INTEGER DEFAULT 1");
        }
    };

    static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE Site_Backup (`key` TEXT NOT NULL, name TEXT, searchable INTEGER, changeable INTEGER, recordable INTEGER, PRIMARY KEY (`key`))");
            database.execSQL("INSERT INTO Site_Backup SELECT `key`, name, searchable, changeable, recordable FROM Site");
            database.execSQL("DROP TABLE Site");
            database.execSQL("ALTER TABLE Site_Backup RENAME to Site");
        }
    };

    static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Live` (`name` TEXT NOT NULL, `boot` INTEGER NOT NULL, `pass` INTEGER NOT NULL, PRIMARY KEY(`name`))");
        }
    };

    static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Prefers.remove("danmu_size");
        }
    };

    static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE Site_Backup (`key` TEXT NOT NULL, searchable INTEGER, changeable INTEGER, PRIMARY KEY (`key`))");
            database.execSQL("INSERT INTO Site_Backup SELECT `key`, searchable, changeable FROM Site");
            database.execSQL("DROP TABLE Site");
            database.execSQL("ALTER TABLE Site_Backup RENAME to Site");
        }
    };

    static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN logo TEXT DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE Download (`id` TEXT NOT NULL, vodPic TEXT, vodName TEXT, url TEXT, header TEXT, createTime INTEGER NOT NULL, PRIMARY KEY (`id`))");
        }
    };

    static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN lastUpdated INTEGER DEFAULT (strftime('%s', 'now'))");
            database.execSQL("ALTER TABLE History ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `FlagScore` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `siteKey` TEXT, `flagName` TEXT, `score` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_FlagScore_siteKey_flagName` ON `FlagScore` (`siteKey`, `flagName`)");
        }
    };

    static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS Site_New (`key` TEXT NOT NULL, searchable INTEGER, changeable INTEGER, blacklist INTEGER NOT NULL, failures INTEGER NOT NULL, PRIMARY KEY (`key`))");
            database.execSQL("INSERT INTO Site_New (`key`, searchable, changeable, blacklist, failures) SELECT `key`, searchable, changeable, 0, 0 FROM Site");
            database.execSQL("DROP TABLE Site");
            database.execSQL("ALTER TABLE Site_New RENAME to Site");
        }
    };

    static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Live ADD COLUMN keep TEXT DEFAULT NULL");
            database.execSQL("DROP TABLE IF EXISTS Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `format` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `History_New` (`key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            database.execSQL("INSERT INTO `History_New` (`key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid`, `lastUpdated`, `deleted`) SELECT `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid`, `lastUpdated`, `deleted` FROM `History` ");
            database.execSQL("DROP TABLE `History` ");
            database.execSQL("ALTER TABLE `History_New` RENAME TO `History` ");
        }
    };

    static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN cache TEXT DEFAULT NULL");
        }
    };

    static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN score INTEGER DEFAULT 0 NOT NULL");
        }
    };

    static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN responseTime INTEGER DEFAULT 0 NOT NULL");
        }
    };

    static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN context TEXT DEFAULT 'vod' NOT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN context TEXT DEFAULT 'vod' NOT NULL");
            database.execSQL("ALTER TABLE Keep ADD COLUMN context TEXT DEFAULT 'vod' NOT NULL");
        }
    };

    static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Favorite` (`key` TEXT NOT NULL, `vodName` TEXT, `vodPic` TEXT, `vodRemarks` TEXT, `createTime` INTEGER NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`key`))");
        }
    };
}
