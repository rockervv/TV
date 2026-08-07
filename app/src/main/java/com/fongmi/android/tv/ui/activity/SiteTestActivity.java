package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivitySiteTestBinding;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

import java.util.HashMap;

public class SiteTestActivity extends BaseActivity implements SiteAdapter.OnClickListener {

    private ActivitySiteTestBinding mBinding;
    private SiteAdapter mAdapter;
    private Site mSite;
    private Result mHomeResult;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SiteTestActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySiteTestBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.siteRecycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.siteRecycler.setAdapter(mAdapter = new SiteAdapter(this));
        mBinding.keywordInput.setText("欧若拉公主");
        if (!VodConfig.get().getSites().isEmpty()) {
            onItemClick(VodConfig.get().getSites().get(0));
        }
    }

    @Override
    protected void initEvent() {
        mBinding.homeBtn.setOnClickListener(v -> testHome());
        mBinding.categoryBtn.setOnClickListener(v -> {
            if (mBinding.tidInput.getText().toString().isEmpty()) pickCategory();
            else testCategory();
        });
        mBinding.categoryBtn.setOnLongClickListener(v -> {
            pickCategory();
            return true;
        });
        mBinding.detailBtn.setOnClickListener(v -> {
            if (mBinding.idInput.getText().toString().isEmpty()) pickVideo(false);
            else testDetail();
        });
        mBinding.detailBtn.setOnLongClickListener(v -> {
            pickVideo(false);
            return true;
        });
        mBinding.playerBtn.setOnClickListener(v -> {
            if (mBinding.idInput.getText().toString().isEmpty()) pickVideo(true);
            else testPlayer();
        });
        mBinding.playerBtn.setOnLongClickListener(v -> {
            pickVideo(true);
            return true;
        });
        mBinding.searchBtn.setOnClickListener(v -> testSearch());
        mBinding.keywordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) testSearch();
            return true;
        });
    }

    private void pickVideo(boolean player) {
        if (mHomeResult == null || mHomeResult.getList().isEmpty()) {
            Notify.show("請先執行 Home 測試以獲取影片列表");
            return;
        }
        String[] names = new String[mHomeResult.getList().size()];
        String[] ids = new String[mHomeResult.getList().size()];
        for (int i = 0; i < mHomeResult.getList().size(); i++) {
            names[i] = mHomeResult.getList().get(i).getVodName();
            ids[i] = mHomeResult.getList().get(i).getVodId();
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("選擇影片")
                .setItems(names, (dialog, which) -> {
                    mBinding.idInput.setText(ids[which]);
                    if (player) testPlayer();
                    else testDetail();
                })
                .show();
    }

    private void pickCategory() {
        if (mHomeResult == null || mHomeResult.getTypes().isEmpty()) {
            Notify.show("請先執行 Home 測試以獲取分類");
            return;
        }
        String[] names = new String[mHomeResult.getTypes().size()];
        String[] ids = new String[mHomeResult.getTypes().size()];
        for (int i = 0; i < mHomeResult.getTypes().size(); i++) {
            names[i] = mHomeResult.getTypes().get(i).getTypeName();
            ids[i] = mHomeResult.getTypes().get(i).getTypeId();
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("選擇分類")
                .setItems(names, (dialog, which) -> {
                    mBinding.tidInput.setText(ids[which]);
                    testCategory();
                })
                .show();
    }

    @Override
    public void onItemClick(Site item) {
        for (Site site : VodConfig.get().getSites()) site.setSelected(site.equals(item));
        mSite = item;
        mHomeResult = null;
        mBinding.siteName.setText(item.getName());
        mBinding.siteConfig.setText(String.format("API: %s\nEXT: %s", item.getApi(), item.getExt()));
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemLongClick(Site item) {
    }

    private void showResult(String method, String text) {
        runOnUiThread(() -> mBinding.resultText.setText(text));
        Log.d("SiteTest", "--- [" + method + "] Result ---\n" + text + "\n----------------" );
    }

    private void testHome() {
        if (mSite == null) return;
        Log.d("SiteTest", "--- [homeContent] Start site: " + mSite.getName());
        new Thread(() -> {
            try {
                Result result = SiteApi.homeContent(mSite);
                mHomeResult = result;
                runOnUiThread(() -> {
                    if (mBinding.tidInput.getText().toString().isEmpty() && !result.getTypes().isEmpty()) {
                        mBinding.tidInput.setText(result.getTypes().get(0).getTypeId());
                    }
                    if (mBinding.idInput.getText().toString().isEmpty() && !result.getList().isEmpty()) {
                        mBinding.idInput.setText(result.getList().get(0).getId());
                    }
                    showResult("homeContent", result.toString());
                });
            } catch (Exception e) {
                showResult("homeContent", "Error: " + e.getMessage());
                Log.e("SiteTest", "homeContent Error", e);
            }
        }).start();
    }

    private void testCategory() {
        if (mSite == null) return;
        String tid = mBinding.tidInput.getText().toString();
        if (TextUtils.isEmpty(tid)) {
            Notify.show("請輸入 TID");
            return;
        }
        Log.d("SiteTest", "--- [categoryContent] Start site: " + mSite.getName() + " tid: " + tid);
        new Thread(() -> {
            try {
                Result result = SiteApi.categoryContent(mSite.getKey(), tid, "1", true, new HashMap<>());
                mHomeResult = result; // Update result to allow picking videos from this category
                showResult("categoryContent", result.toString());
            } catch (Exception e) {
                showResult("categoryContent", "Error: " + e.getMessage());
                Log.e("SiteTest", "categoryContent Error", e);
            }
        }).start();
    }

    private void testDetail() {
        if (mSite == null) return;
        String id = mBinding.idInput.getText().toString();
        if (TextUtils.isEmpty(id)) {
            Notify.show("請輸入 VID");
            return;
        }
        Log.d("SiteTest", "--- [detailContent] Start site: " + mSite.getName() + " id: " + id);
        new Thread(() -> {
            try {
                Result result = SiteApi.detailContent(mSite.getKey(), id);
                showResult("detailContent", result.toString());
            } catch (Exception e) {
                showResult("detailContent", "Error: " + e.getMessage());
                Log.e("SiteTest", "detailContent Error", e);
            }
        }).start();
    }

    private void testSearch() {
        if (mSite == null) return;
        String keyword = mBinding.keywordInput.getText().toString();
        if (TextUtils.isEmpty(keyword)) {
            Notify.show("請輸入關鍵字");
            return;
        }
        Log.d("SiteTest", "--- [searchContent] Start site: " + mSite.getName() + " keyword: " + keyword);
        new Thread(() -> {
            try {
                Result result = SiteApi.searchContent(mSite, keyword, false, "1");
                showResult("searchContent", result.toString());
            } catch (Exception e) {
                showResult("searchContent", "Error: " + e.getMessage());
                Log.e("SiteTest", "searchContent Error", e);
            }
        }).start();
    }

    private void testPlayer() {
        if (mSite == null) return;
        String id = mBinding.idInput.getText().toString();
        if (TextUtils.isEmpty(id)) {
            Notify.show("請輸入 VID (Player 測試使用 ID 欄位)");
            return;
        }
        Log.d("SiteTest", "--- [playerContent] Start site: " + mSite.getName() + " id: " + id);
        new Thread(() -> {
            try {
                Result result = SiteApi.playerContent(mSite.getKey(), "", id);
                showResult("playerContent", result.toString());
            } catch (Exception e) {
                showResult("playerContent", "Error: " + e.getMessage());
                Log.e("SiteTest", "playerContent Error", e);
            }
        }).start();
    }
}
