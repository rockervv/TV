package com.fongmi.android.tv.ui.adapter;

import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Favorite;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterFavoriteSwitchBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;
    public static final int TYPE_ADD = 2;
    public static final int TYPE_ACTION = 3;
    public static final int TYPE_LINE = 4;
    public static final int TYPE_BACK = 5;

    public static final int LEVEL_MAIN = 0;
    public static final int LEVEL_HISTORY = 1;
    public static final int LEVEL_KEEP = 2;

    private final OnClickListener mListener;
    private final List<Item> mItems;
    private final Map<String, String> mTotal;
    private final Map<String, Boolean> mUpdates;
    private final Map<String, Boolean> mErrors;
    private final Map<String, Boolean> mChecking;
    private final Map<String, Boolean> mChecked;
    private int level;

    public FavoriteAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
        this.mTotal = new HashMap<>();
        this.mUpdates = new HashMap<>();
        this.mErrors = new HashMap<>();
        this.mChecking = new HashMap<>();
        this.mChecked = new HashMap<>();
        this.level = LEVEL_MAIN;
    }

    public interface OnClickListener {
        void onItemClick(Item item);
        void onItemLongClick(Item item);
        boolean onItemKey(Item item, int keyCode, android.view.KeyEvent event);
    }

    public static class Item {
        public int type;
        public String title;
        public Favorite favorite;
        public History history;
        public Keep keep;
        private History h;

        public static Item action(String title) {
            Item item = new Item();
            item.type = TYPE_ACTION;
            item.title = title;
            return item;
        }

        public static Item line() {
            Item item = new Item();
            item.type = TYPE_LINE;
            return item;
        }

        public static Item back() {
            Item item = new Item();
            item.type = TYPE_BACK;
            return item;
        }

        public static Item header(String title) {
            Item item = new Item();
            item.type = TYPE_HEADER;
            item.title = title;
            return item;
        }

        public static Item add(History history) {
            Item item = new Item();
            item.type = TYPE_ADD;
            item.history = history;
            return item;
        }

        public static Item favorite(Favorite favorite) {
            Item item = new Item();
            item.type = TYPE_ITEM;
            item.favorite = favorite;
            return item;
        }

        public static Item history(History history) {
            Item item = new Item();
            item.type = TYPE_ITEM;
            item.history = history;
            return item;
        }

        public static Item keep(Keep keep) {
            Item item = new Item();
            item.type = TYPE_ITEM;
            item.keep = keep;
            return item;
        }

        public String getName() {
            if (favorite != null) return favorite.getVodName();
            if (history != null) return history.getVodName();
            if (keep != null) return keep.getVodName();
            return "";
        }

        public String getPic() {
            if (favorite != null) return favorite.getVodPic();
            if (history != null) return history.getVodPic();
            if (keep != null) return keep.getVodPic();
            return "";
        }

        public String getRemarks() {
            if (favorite != null) return favorite.getVodRemarks();
            if (history != null) return history.getVodRemarks();
            if (keep != null) {
                History h = getHistory();
                return h != null ? h.getVodRemarks() : "";
            }
            return "";
        }

        public History getHistory() {
            if (history != null) return history;
            if (h == null) h = History.find(getKey());
            return h;
        }

        public String getKey() {
            if (favorite != null) return favorite.getKey();
            if (history != null) return history.getKey();
            if (keep != null) return keep.getKey();
            return "";
        }

        public String getSiteKey() {
            if (favorite != null) return favorite.getSiteKey();
            if (history != null) return history.getSiteKey();
            if (keep != null) return keep.getSiteKey();
            return "";
        }

        public String getVodId() {
            if (favorite != null) return favorite.getVodId();
            if (history != null) return history.getVodId();
            if (keep != null) return keep.getVodId();
            return "";
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void build(History current, List<Favorite> favorites, List<History> histories, List<Keep> keeps) {
        Log.d("FavoriteAdapter", "build: level=" + level + ", current=" + (current != null ? current.getVodName() : "null"));
        mItems.clear();
        if (level == LEVEL_MAIN) {
            mItems.add(Item.action(ResUtil.getString(R.string.favorite_check_all)));
            mItems.add(Item.line());
            
            boolean exist = false;
            if (current != null) {
                for (Favorite f : favorites) if (f.getKey().equals(current.getKey())) { exist = true; break; }
                if (!exist) mItems.add(Item.add(current));
            }

            for (Favorite f : favorites) mItems.add(Item.favorite(f));
            
            mItems.add(Item.line());
            mItems.add(Item.header(ResUtil.getString(R.string.favorite_history)));
            mItems.add(Item.header(ResUtil.getString(R.string.favorite_keep)));
        } else if (level == LEVEL_HISTORY) {
            mItems.add(Item.back());
            mItems.add(Item.line());
            for (History h : histories) mItems.add(Item.history(h));
        } else if (level == LEVEL_KEEP) {
            mItems.add(Item.back());
            mItems.add(Item.line());
            for (Keep k : keeps) mItems.add(Item.keep(k));
        }
        notifyDataSetChanged();
    }

    public void checkUpdateAll() {
        for (Item item : mItems) {
            if (item.type == TYPE_ITEM && item.favorite != null) {
                checkUpdate(item);
            }
        }
    }

    public void checkUpdate(Item item) {
        if (item.favorite == null || mChecking.containsKey(item.getKey())) return;
        mChecking.put(item.getKey(), true);
        mChecked.remove(item.getKey());
        notifyItemChanged(mItems.indexOf(item));
        Task.execute(() -> {
            Result result = SiteApi.detailContent(item.getSiteKey(), item.getVodId(), false);
            App.post(() -> {
                mChecking.remove(item.getKey());
                mChecked.put(item.getKey(), true);
                int index = mItems.indexOf(item);
                if (index == -1) return;
                if (result.getList().isEmpty()) {
                    mErrors.put(item.getKey(), true);
                } else if (result.getVod() != null && !TextUtils.isEmpty(result.getVod().getRemarks())) {
                    mTotal.put(item.getKey(), result.getVod().getRemarks());
                    if (!result.getVod().getRemarks().equals(item.getRemarks())) {
                        mUpdates.put(item.getKey(), true);
                    } else {
                        mUpdates.remove(item.getKey());
                        mErrors.remove(item.getKey());
                    }
                }
                notifyItemChanged(index);
            });
        });
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterFavoriteSwitchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = mItems.get(position);
        boolean isHeader = item.type == TYPE_HEADER;
        boolean isAdd = item.type == TYPE_ADD;
        boolean isAction = item.type == TYPE_ACTION;
        boolean isLine = item.type == TYPE_LINE;
        boolean isBack = item.type == TYPE_BACK;

        holder.binding.header.setVisibility(isHeader ? View.VISIBLE : View.GONE);
        holder.binding.lineLayout.setVisibility(isLine ? View.VISIBLE : View.GONE);
        holder.binding.item.setVisibility(isHeader || isLine ? View.GONE : View.VISIBLE);
        holder.binding.arrow.setVisibility(View.GONE);

        if (isHeader) {
            holder.binding.header.setText(item.title);
            holder.binding.header.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_right, 0);
        } else if (isLine) {
            // Nothing
        } else if (isBack) {
            holder.binding.name.setText("回上一層");
            holder.binding.remark.setVisibility(View.GONE);
            holder.binding.image.setImageResource(R.drawable.ic_keyboard_left);
        } else if (isAction) {
            holder.binding.name.setText(item.title);
            holder.binding.remark.setVisibility(View.GONE);
            holder.binding.image.setImageResource(R.drawable.ic_setting_refresh);
        } else if (isAdd) {
            holder.binding.name.setText(ResUtil.getString(R.string.favorite_add, item.getName()));
            holder.binding.remark.setVisibility(View.GONE);
            ImgUtil.load(item.getName(), item.getPic(), holder.binding.image, true);
        } else {
            holder.binding.name.setText(item.getName());
            History history = item.getHistory();
            String watched = item.getRemarks();
            String total = mTotal.getOrDefault(item.getKey(), "");
            if (history != null && history.getDuration() > 0 && (Util.getNumber(watched) == -1 || Util.getNumber(total) == 1)) {
                holder.binding.remark.setText(ResUtil.getString(R.string.favorite_remark, Util.timeMs(history.getPosition()), Util.timeMs(history.getDuration())));
            } else if (TextUtils.isEmpty(total)) {
                holder.binding.remark.setText(watched);
            } else {
                holder.binding.remark.setText(ResUtil.getString(R.string.favorite_remark, watched, total));
            }
            holder.binding.remark.setVisibility(TextUtils.isEmpty(holder.binding.remark.getText()) ? View.GONE : View.VISIBLE);
            
            if (mChecking.containsKey(item.getKey())) {
                holder.binding.remark.setText(R.string.favorite_checking);
                holder.binding.remark.setTextColor(Color.LTGRAY);
            } else if (mErrors.containsKey(item.getKey())) {
                holder.binding.remark.setText(R.string.error_detail);
                holder.binding.remark.setTextColor(Color.GRAY);
                holder.binding.name.setTextColor(Color.GRAY);
            } else if (mUpdates.containsKey(item.getKey())) {
                holder.binding.remark.setTextColor(Color.YELLOW);
                holder.binding.name.setTextColor(Color.WHITE);
            } else if (mChecked.containsKey(item.getKey())) {
                holder.binding.remark.setTextColor(Color.GREEN);
                holder.binding.name.setTextColor(Color.WHITE);
            } else {
                holder.binding.remark.setTextColor(Color.WHITE);
                holder.binding.name.setTextColor(Color.WHITE);
            }
            ImgUtil.load(item.getName(), item.getPic(), holder.binding.image, true);
        }

        if (!isLine) {
            holder.binding.getRoot().setFocusable(true);
            holder.binding.getRoot().setOnClickListener(v -> {
                Log.d("FavoriteAdapter", "onClick: pos=" + holder.getLayoutPosition() + ", type=" + item.type + ", title=" + item.title);
                mListener.onItemClick(item);
            });
            holder.binding.getRoot().setOnLongClickListener(v -> {
                mListener.onItemLongClick(item);
                return true;
            });
            holder.binding.getRoot().setOnKeyListener((v, keyCode, event) -> mListener.onItemKey(item, keyCode, event));
            holder.binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
                if (item.type == TYPE_ITEM || item.type == TYPE_ADD || item.type == TYPE_HEADER) {
                    holder.binding.arrow.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                }
            });
            // Ensure header has the correct background for focus
            if (isHeader) holder.binding.getRoot().setBackgroundResource(R.drawable.selector_favorite);
            else holder.binding.getRoot().setBackgroundResource(0);
        } else {
            holder.binding.getRoot().setFocusable(false);
            holder.itemView.setFocusable(false);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final AdapterFavoriteSwitchBinding binding;
        public ViewHolder(@NonNull AdapterFavoriteSwitchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
