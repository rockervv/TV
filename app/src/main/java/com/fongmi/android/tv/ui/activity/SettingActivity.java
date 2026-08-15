package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.HistorySyncManager;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.BackupCallback;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.DohCallback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.ProxyCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.BackupAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.BackupDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.DohDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.ProxyDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.orhanobut.logger.Logger;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements BackupCallback, ConfigCallback, SiteCallback, LiveCallback, DohCallback, ProxyCallback {

    private ActivitySettingBinding mBinding;
    private String[] backup;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setCacheText();
        setOtherText();
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.proxyText.setText(UrlUtil.scheme(Setting.getProxy()));
        mBinding.backupText.setText((backup = ResUtil.getStringArray(R.array.select_backup))[Setting.getBackupMode()]);
        mBinding.categoryCacheText.setText(Setting.getSwitch(Setting.isCategoryCache()));
        mBinding.aboutText.setText("leanback-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        android.util.Log.d("initEvent", "initEvent START");
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.proxy.setOnClickListener(this::onProxy);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.cache.setOnLongClickListener(this::onCacheLongClick);
        mBinding.categoryCache.setOnClickListener(this::onCategoryCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.spider.setOnClickListener(this::onSpider);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.backup.setOnLongClickListener(this::onBackupMode);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.version.setOnLongClickListener(this::onVersionDev);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.custom.setOnClickListener(this::onCustom);
        mBinding.custom.setOnLongClickListener(this::onCustomLongClick);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.about.setOnClickListener(this::onAbout);
        mBinding.syncSetting.setOnClickListener(this::onSyncSetting);
        mBinding.sync.setOnClickListener(this::onSync);
        android.util.Log.d("initEvent", "initEvent SUCCESS");
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, (allGranted, grantedList, deniedList) -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                mBinding.vodUrl.setText(config.getDesc());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                mBinding.liveUrl.setText(config.getDesc());
                break;
            case 2:
                WallConfig.load(config, getCallback());
                mBinding.wallUrl.setText(config.getDesc());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        RefreshEvent.home();
    }

    @Override
    public void onChanged() {
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create(this).type(0).show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create(this).type(1).show(this);
    }

    private void onWall(View view) {
        ConfigDialog.create(this).type(2).show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).type(0).edit().show(this  );
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create(this).type(1).edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create(this).type(2).edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onSpider(View view) {
        SettingSpiderActivity.start(this);
    }

    private void onVersion(View view) {
        Updater.get().force().release().start(this);
    }

    private boolean onVersionDev(View view) {
        Updater.get().force().dev().start(this);
        return true;
    }

    private void onSyncSetting(View view) {
        SyncDialog.create(this).show(this);
    }

    private void onSync(View view) {
        HistorySyncManager.SyncAll();
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        RefreshEvent.wall();
    }

    private void setWallRefresh(View view) {
        WallConfig.get().load(getCallback());
    }

    private void onCustom(View view) {
        SettingCustomActivity.start(this);
    }

    private boolean onCustomLongClick(View view) {
        SiteTestActivity.start(this);
        return true;
    }

    private void onAbout(View view) {
        mBinding.aboutText.setText("leanback-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi);
    }

    private void setDoh(View view) {
        DohDialog.create(this).index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
        Source.get().stop();
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
        VodConfig.load(Config.vod(), getCallback());
    }

    private void onProxy(View view) {
        ProxyDialog.create(this).show(this);
    }

    @Override
    public void setProxy(String proxy) {
        Source.get().stop();
        Setting.putProxy(proxy);
        OkHttp.selector().clear();
        OkHttp.get().setProxy(proxy);
        VodConfig.load(Config.vod(), getCallback());
        mBinding.proxyText.setText(UrlUtil.scheme(proxy));
    }

    private void onCategoryCache(View view) {
        Setting.putCategoryCache(!Setting.isCategoryCache());
        mBinding.categoryCacheText.setText(Setting.getSwitch(Setting.isCategoryCache()));
    }

    private void onCache(View view) {
        FileUtil.clearCache(false, new Callback() {
            @Override
            public void success() {
                com.fongmi.android.tv.api.CacheManager.clear();
                VodConfig.get().getConfig().json("").save();
                setCacheText();
            }
        });
    }

    private boolean onCacheLongClick(View view) {
        FileUtil.clearCache(true, new Callback() {
            @Override
            public void success() {
                com.fongmi.android.tv.api.CacheManager.clear();
                setCacheText();
                Config config = VodConfig.get().getConfig().json("").save();
                if (!config.isEmpty()) setConfig(config);
            }
        });
        return true;
    }

    @Override
    public void restore(File file) {
        AppDatabase.restore(file, new Callback() {
            @Override
            public void success() {
                Notify.progress(getActivity());
                App.post(() -> {
                    AppDatabase.reset();
                    initConfig();
                    Notify.dismiss();
                    Notify.show(R.string.restored);
                }, 3000);
            }

            @Override
            public void error() {
                Notify.show(R.string.error_config_parse);
            }
        });
    }

    private void onRestore(View view) {
        android.util.Log.d("Backup", "onRestore clicked");
        PermissionUtil.requestFile(this, (allGranted, grantedList, deniedList) -> {
            android.util.Log.d("Backup", "onRestore permission: " + allGranted);
            App.post(() -> {
                if (new BackupAdapter(null).addAll().getItemCount() == 0) {
                    Notify.show(R.string.error_empty);
                } else {
                    android.util.Log.d("Backup", "Showing BackupDialog");
                    BackupDialog.create(this).show(this);
                }
            });
        });
    }

    private void initConfig() {
        WallConfig.get().init();
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback());
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, (allGranted, grantedList, deniedList) -> AppDatabase.backup(new Callback() {
            @Override
            public void success(String path) {
                Notify.show(R.string.backed);
            }
        }));
    }

    private boolean onBackupMode(View view) {
        int index = Setting.getBackupMode() == backup.length - 1 ? 0 : Setting.getBackupMode() + 1;
        Setting.putBackupMode(index);
        mBinding.backupText.setText(backup[index]);
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        setCacheText();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefreshEvent.history();
    }
}
