package com.coderpage.mine.app.tally.edit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.coderpage.base.common.IError;
import com.coderpage.base.common.NonThrowError;
import com.coderpage.concurrency.AsyncTaskExecutor;
import com.coderpage.framework.Model;
import com.coderpage.framework.QueryEnum;
import com.coderpage.base.common.SimpleCallback;
import com.coderpage.framework.UserActionEnum;
import com.coderpage.mine.app.tally.common.error.ErrorCode;
import com.coderpage.mine.app.tally.data.CategoryIconHelper;
import com.coderpage.mine.app.tally.data.Category;
import com.coderpage.mine.app.tally.data.Expense;
import com.coderpage.mine.app.tally.provider.ProviderUtils;
import com.coderpage.mine.app.tally.provider.TallyContract;
import com.coderpage.mine.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static com.coderpage.base.utils.LogUtils.LOGE;
import static com.coderpage.base.utils.LogUtils.makeLogTag;

/**
 * @author abner-l. 2017-04-16
 */

class ExpenseEditModel implements Model<ExpenseEditModel.EditQueryEnum
        , ExpenseEditModel.EditUserActionEnum, ExpenseEditModel, IError> {
    private static final String TAG = makeLogTag(ExpenseEditModel.class);

    static final String EXTRA_EXPENSE_ID = "extra_expense_id";
    static final String EXTRA_EXPENSE_AMOUNT = "extra_expense_amount";
    static final String EXTRA_EXPENSE_CATEGORY_ID = "extra_expense_category_id";
    static final String EXTRA_EXPENSE_CATEGORY = "extra_expense_category";
    static final String EXTRA_EXPENSE_CATEGORY_ICON_RES_ID = "extra_expense_category_icon_res_id";
    static final String EXTRA_EXPENSE_DESC = "extra_expense_desc";
    static final String EXTRA_EXPENSE_TIME = "extra_expense_time";

    private Context mContext;
    private Expense mExpense;
    private List<Category> mCategoryList = new ArrayList<>();

    ExpenseEditModel(Context context, long expenseId) {
        mContext = context;
        mExpense = new Expense();
        mExpense.setId(expenseId);
    }

    List<Category> getCategoryItemList() {
        return mCategoryList;
    }

    Expense getExpenseItem() {
        return mExpense;
    }

    @Override
    public EditQueryEnum[] getQueries() {
        return EditQueryEnum.values();
    }

    @Override
    public EditUserActionEnum[] getUserActions() {
        return EditUserActionEnum.values();
    }

    @Override
    public void requestData(
            EditQueryEnum query,
            DataQueryCallback<ExpenseEditModel, EditQueryEnum, IError> callback) {

        switch (query) {
            case LOAD_CATEGORY:
                loadCategoryAsync((result) -> {
                    if (result != null) {
                        mCategoryList.clear();
                        mCategoryList.addAll(result);
                        callback.onModelUpdated(ExpenseEditModel.this, query);
                    } else {
                        callback.onError(query, new NonThrowError(ErrorCode.UNKNOWN, ""));
                    }
                });
                break;

            case LOAD_EXPENSE:
                // 没有记录ID，说明为创建一个新纪录，赋值为默认值
                if (mExpense.getId() == 0) {
                    queryFirstPlaceCategory((item) -> {
                        if (item == null) {
                            callback.onError(query, new NonThrowError(ErrorCode.UNKNOWN, ""));
                        } else {
                            mExpense.setAmount(0.0F);
                            mExpense.setCategoryIconResId(item.getIcon());
                            mExpense.setTime(System.currentTimeMillis());
                            mExpense.setCategoryId(item.getId());
                            mExpense.setCategoryName(item.getName());
                            mExpense.setSyncId(AndroidUtils.generateUUID());
                            callback.onModelUpdated(ExpenseEditModel.this, query);
                        }
                    });
                } else {
                    // 读取当前记录最新数据
                    queryExpenseByIdAsync(mExpense.getId(), (item) -> {
                        if (item == null) {
                            callback.onError(query, new NonThrowError(ErrorCode.UNKNOWN, ""));
                        } else {
                            mExpense = item;
                            callback.onModelUpdated(ExpenseEditModel.this, query);
                        }
                    });
                }
                break;
        }
    }

    @Override
    public void deliverUserAction(
            EditUserActionEnum action,
            @Nullable Bundle args,
            UserActionCallback<ExpenseEditModel, EditUserActionEnum, IError> callback) {

        switch (action) {

            case AMOUNT_CHANGED:
                if (args == null || !args.containsKey(EXTRA_EXPENSE_AMOUNT)) {
                    throw new IllegalArgumentException("miss extra " + EXTRA_EXPENSE_AMOUNT);
                }
                mExpense.setAmount(args.getFloat(EXTRA_EXPENSE_AMOUNT));
                LOGE(TAG, "update expense amount " + args.getFloat(EXTRA_EXPENSE_AMOUNT));
                callback.onModelUpdated(ExpenseEditModel.this, action);
                break;

            case CATEGORY_CHANGED:
                if (args == null || !args.containsKey(EXTRA_EXPENSE_CATEGORY)
                        || !args.containsKey(EXTRA_EXPENSE_CATEGORY_ID)
                        || !args.containsKey(EXTRA_EXPENSE_CATEGORY_ICON_RES_ID)) {
                    throw new IllegalArgumentException("miss extra "
                            + EXTRA_EXPENSE_CATEGORY + " OR " + EXTRA_EXPENSE_CATEGORY_ID
                            + " OR " + EXTRA_EXPENSE_CATEGORY_ICON_RES_ID);
                }
                mExpense.setCategoryIconResId(args.getInt(EXTRA_EXPENSE_CATEGORY_ICON_RES_ID));
                mExpense.setCategoryName(args.getString(EXTRA_EXPENSE_CATEGORY));
                mExpense.setCategoryId(args.getLong(EXTRA_EXPENSE_CATEGORY_ID));
                callback.onModelUpdated(ExpenseEditModel.this, action);
                break;

            case DESC_CHANGED:
                if (args == null || !args.containsKey(EXTRA_EXPENSE_DESC)) {
                    throw new IllegalArgumentException("miss extra " + EXTRA_EXPENSE_DESC);
                }
                mExpense.setDesc(args.getString(EXTRA_EXPENSE_DESC, ""));
                callback.onModelUpdated(ExpenseEditModel.this, action);
                break;

            case DATE_CHANGED:
                if (args == null || !args.containsKey(EXTRA_EXPENSE_TIME)) {
                    throw new IllegalArgumentException("miss extra " + EXTRA_EXPENSE_TIME);
                }
                mExpense.setTime(args.getLong(EXTRA_EXPENSE_TIME));
                callback.onModelUpdated(ExpenseEditModel.this, action);
                break;

            case SAVE_DATA:
                LOGE(TAG, "save data amount " + mExpense.getAmount());
                saveExpenseAsync(mExpense, (eid) -> {
                    if (eid > 0) {
                        mExpense.setId(eid);
                        mExpense.setCategoryIconResId(
                                CategoryIconHelper.resId(mExpense.getCategoryName()));
                        callback.onModelUpdated(ExpenseEditModel.this, action);
                    } else {
                        callback.onError(action, new NonThrowError(ErrorCode.UNKNOWN, ""));
                    }
                });
                break;
        }
    }

    @Override
    public void cleanUp() {

    }

    private void queryFirstPlaceCategory(SimpleCallback<Category> callback) {
        new AsyncTask<Void, Void, Category>() {
            @Override
            protected Category doInBackground(Void... params) {
                Cursor cursor = mContext.getContentResolver().query(
                        TallyContract.Category.CONTENT_URI,
                        null, null, null, "category_order DESC");
                if (cursor == null) {
                    return null;
                }
                Category item = null;
                if (cursor.moveToFirst()) {
                    item = Category.fromCursor(cursor);
                }
                cursor.close();
                return item;
            }

            @Override
            protected void onPostExecute(Category item) {
                callback.success(item);
            }
        }.executeOnExecutor(AsyncTaskExecutor.executor());
    }

    private void loadCategoryAsync(SimpleCallback<List<Category>> callback) {
        new AsyncTask<Void, Void, List<Category>>() {
            @Override
            protected List<Category> doInBackground(Void... params) {
                Cursor cursor = mContext.getContentResolver().query(
                        TallyContract.Category.CONTENT_URI,
                        null, null, null, "category_order DESC");
                if (cursor == null) {
                    return null;
                }
                List<Category> result = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    result.add(Category.fromCursor(cursor));
                }
                cursor.close();
                return result;
            }

            @Override
            protected void onPostExecute(List<Category> categories) {
                callback.success(categories);
            }
        }.executeOnExecutor(AsyncTaskExecutor.executor());
    }


    private void queryExpenseByIdAsync(long expenseId, SimpleCallback<Expense> callback) {
        new AsyncTask<Void, Void, Expense>() {
            @Override
            protected Expense doInBackground(Void... params) {
                Cursor cursor = mContext.getContentResolver().query(
                        TallyContract.Expense.CONTENT_URI,
                        null,
                        TallyContract.Expense._ID + "=?",
                        new String[]{String.valueOf(expenseId)},
                        null
                );
                Expense item = null;
                if (cursor == null) return null;
                if (cursor.moveToFirst()) {
                    item = Expense.fromCursor(cursor);
                }
                cursor.close();
                return item;
            }

            @Override
            protected void onPostExecute(Expense item) {
                callback.success(item);
            }
        }.executeOnExecutor(AsyncTaskExecutor.executor());
    }

    private void saveExpenseAsync(Expense expense, SimpleCallback<Long> callback) {
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(TallyContract.Expense.AMOUNT, expense.getAmount());
                values.put(TallyContract.Expense.CATEGORY_ID, expense.getCategoryId());
                values.put(TallyContract.Expense.CATEGORY, expense.getCategoryName());
                values.put(TallyContract.Expense.DESC,
                        expense.getDesc() == null ? "" : expense.getDesc());
                values.put(TallyContract.Expense.TIME, expense.getTime());

                // categoryId 大于0，更新记录
                // 否则插入新记录
                if (expense.getId() > 0) {
                    mContext.getContentResolver().update(
                            TallyContract.Expense.CONTENT_URI,
                            values,
                            TallyContract.Expense._ID + "=?",
                            new String[]{String.valueOf(expense.getId())});
                    return mExpense.getId();
                } else {
                    values.put(TallyContract.Expense.SYNC_ID, mExpense.getSyncId());
                    Uri uri = mContext.getContentResolver()
                            .insert(TallyContract.Expense.CONTENT_URI, values);
                    return ProviderUtils.parseIdFromUri(uri);
                }
            }

            @Override
            protected void onPostExecute(Long id) {
                callback.success(id);
            }
        }.executeOnExecutor(AsyncTaskExecutor.executor());
    }

    enum EditQueryEnum implements QueryEnum {
        LOAD_EXPENSE(1, null),
        LOAD_CATEGORY(2, null);

        private int id;
        private String[] projection;

        EditQueryEnum(int id, String[] projection) {
            this.id = id;
            this.projection = projection;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String[] getProjection() {
            return projection;
        }
    }

    enum EditUserActionEnum implements UserActionEnum {
        RELOAD(1),
        SAVE_DATA(2),
        CATEGORY_CHANGED(3),
        DATE_CHANGED(4),
        AMOUNT_CHANGED(5),
        DESC_CHANGED(6);

        private int id;

        EditUserActionEnum(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }
    }
}
