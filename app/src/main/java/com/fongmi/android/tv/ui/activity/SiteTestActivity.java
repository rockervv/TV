package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
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
        
        mBinding.homeBtn.setNextFocusRightId(R.id.categoryBtn);
        mBinding.categoryBtn.setNextFocusLeftId(R.id.homeBtn);
        mBinding.categoryBtn.setNextFocusRightId(R.id.detailBtn);
        mBinding.detailBtn.setNextFocusLeftId(R.id.categoryBtn);
        mBinding.detailBtn.setNextFocusRightId(R.id.searchBtn);
        mBinding.searchBtn.setNextFocusLeftId(R.id.detailBtn);
        mBinding.searchBtn.setNextFocusRightId(R.id.playerBtn);
        mBinding.playerBtn.setNextFocusLeftId(R.id.searchBtn);

        if (!VodConfig.get().getSites().isEmpty()) {
            onItemClick(VodConfig.get().getSites().get(0));
        }
    }

    private void focusSelectedSite() {
        int position = -1;
        java.util.List<Site> sites = VodConfig.get().getSites();
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).isSelected()) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            final int pos = position;
            mBinding.siteRecycler.scrollToPosition(pos);
            mBinding.siteRecycler.post(() -> {
                androidx.recyclerview.widget.RecyclerView.ViewHolder vh = mBinding.siteRecycler.findViewHolderForAdapterPosition(pos);
                if (vh != null) vh.itemView.requestFocus();
                else mBinding.siteRecycler.requestFocus();
            });
        } else {
            mBinding.siteRecycler.requestFocus();
        }
    }

    @Override
    protected void initEvent() {
        mBinding.homeBtn.setOnClickListener(v -> testHome());
        mBinding.homeBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                focusSelectedSite();
                return true;
            }
            return false;
        });

        mBinding.categoryBtn.setOnClickListener(v -> pickCategory());
        mBinding.categoryBtn.setOnLongClickListener(v -> {
            testCategory(mBinding.tidInput.getText().toString());
            return true;
        });

        mBinding.detailBtn.setOnClickListener(v -> pickVideo(false));
        mBinding.detailBtn.setOnLongClickListener(v -> {
            testDetail(mBinding.idInput.getText().toString());
            return true;
        });

        mBinding.playerBtn.setOnClickListener(v -> pickVideo(true));
        mBinding.playerBtn.setOnLongClickListener(v -> {
            testPlayerChain(mBinding.idInput.getText().toString());
            return true;
        });

        mBinding.searchBtn.setOnClickListener(v -> testSearch());
        mBinding.keywordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) testSearch();
            return true;
        });

        mBinding.siteRecycler.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mBinding.homeBtn.requestFocus();
                return true;
            }
            return false;
        });
    }

    private void pickVideo(boolean isPlayer) {
        if (mHomeResult == null || mHomeResult.getList().isEmpty()) {
            Notify.show("請先執行 Home/Category/Search 以獲取影片列表");
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
                    if (isPlayer) testPlayerChain(ids[which]);
                    else testDetail(ids[which]);
                })
                .show();
    }

    private void pickCategory() {
        if (mHomeResult == null || mHomeResult.getTypes().isEmpty()) {
            Notify.show("請先執行 Home 以獲取分類列表");
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
                    testCategory(ids[which]);
                })
                .show();
    }

    @Override
    public void onItemClick(Site item) {
        View focus = getCurrentFocus();
        for (Site site : VodConfig.get().getSites()) site.setSelected(site.equals(item));
        mSite = item;
        mHomeResult = null;
        mBinding.siteName.setText(item.getName());
        mBinding.siteConfig.setText(String.format("TYPE: %d | API: %s\nEXT: %s", item.getType(), item.getApi(), item.getExt()));
        mAdapter.notifyDataSetChanged();
        if (focus != null) focus.requestFocus();
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
        Log.d("SiteTest", ">>> [homeContent] START Site: " + mSite.getName());
        new Thread(() -> {
            try {
                // 🛠️ 給予 init() 背景預熱任務 (指紋同步) 一點緩衝時間
                Thread.sleep(500);
                // 🛠️ 在測試模式中強迫不使用快取 (Force Refresh)
                Result result = SiteApi.homeContent(mSite, true);
                mHomeResult = result;
                showResult("homeContent", result.toString());
            } catch (Exception e) {
                showResult("homeContent", "Error: " + e.getMessage());
            }
        }).start();
    }

    private void testCategory(String tid) {
        if (mSite == null || TextUtils.isEmpty(tid)) return;
        Log.d("SiteTest", ">>> [categoryContent] START TID: " + tid);
        new Thread(() -> {
            try {
                // 🛠️ 在測試模式中強迫不使用快取 (Force Refresh)
                Result result = SiteApi.categoryContent(mSite.getKey(), tid, "1", true, new HashMap<>(), true);
                mHomeResult = result; 
                showResult("categoryContent", result.toString());
            } catch (Exception e) {
                showResult("categoryContent", "Error: " + e.getMessage());
            }
        }).start();
    }

    private void testDetail(String id) {
        if (mSite == null || TextUtils.isEmpty(id)) return;
        Log.d("SiteTest", ">>> [detailContent] START VID: " + id);
        new Thread(() -> {
            try {
                Result result = SiteApi.detailContent(mSite.getKey(), id);
                showResult("detailContent", result.toString());
            } catch (Exception e) {
                showResult("detailContent", "Error: " + e.getMessage());
            }
        }).start();
    }

    private void testSearch() {
        if (mSite == null) return;
        String keyword = mBinding.keywordInput.getText().toString();
        Log.d("SiteTest", ">>> [searchContent] START KEYWORD: " + keyword);
        new Thread(() -> {
            try {
                Result result = SiteApi.searchContent(mSite, keyword, false, "1");
                if (!result.getList().isEmpty()) mHomeResult = result;
                showResult("searchContent", result.toString());
            } catch (Exception e) {
                showResult("searchContent", "Error: " + e.getMessage());
            }
        }).start();
    }

    private void testPlayerChain(String id) {
        if (mSite == null || TextUtils.isEmpty(id)) return;
        new Thread(() -> {
            try {
                Result detail = SiteApi.detailContent(mSite.getKey(), id);
                if (detail.getList().isEmpty()) {
                    showResult("playerContent", "Error: Detail empty");
                    return;
                }
                Vod vod = detail.getVod();
                String[] flags = vod.getPlayFrom().split("\\$\\$\\$");
                String[] urlRows = vod.getPlayUrl().split("\\$\\$\\$");
                runOnUiThread(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("選擇線路")
                            .setItems(flags, (d1, whichFlag) -> {
                                String flag = flags[whichFlag];
                                String[] episodes = urlRows[whichFlag].split("#");
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                        .setTitle("選擇集數")
                                        .setItems(episodes, (d2, whichEp) -> {
                                            String[] epInfo = episodes[whichEp].split("\\$");
                                            String finalUrl = epInfo.length > 1 ? epInfo[1] : epInfo[0];
                                            executePlayerTest(flag, finalUrl);
                                        }).show();
                            }).show();
                });
            } catch (Exception e) {
                showResult("playerContent", "Error: " + e.getMessage());
            }
        }).start();
    }

    private void executePlayerTest(String flag, String url) {
        new Thread(() -> {
            try {
                Result result = SiteApi.playerContent(mSite.getKey(), flag, url);
                showResult("playerContent", result.toString());
            } catch (Exception e) {
                showResult("playerContent", "Error: " + e.getMessage());
            }
        }).start();
    }
}
