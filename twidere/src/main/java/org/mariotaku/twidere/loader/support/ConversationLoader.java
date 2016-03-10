/*
 * 				Twidere - Twitter client for Android
 * 
 *  Copyright (C) 2012-2014 Mariotaku Lee <mariotaku.lee@gmail.com>
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.loader.support;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import org.mariotaku.twidere.api.twitter.Twitter;
import org.mariotaku.twidere.api.twitter.TwitterException;
import org.mariotaku.twidere.api.twitter.model.Paging;
import org.mariotaku.twidere.api.twitter.model.SearchQuery;
import org.mariotaku.twidere.api.twitter.model.Status;
import org.mariotaku.twidere.model.ParcelableCredentials;
import org.mariotaku.twidere.model.ParcelableStatus;
import org.mariotaku.twidere.model.util.ParcelableStatusUtils;
import org.mariotaku.twidere.util.InternalTwitterContentUtils;
import org.mariotaku.twidere.util.Nullables;
import org.mariotaku.twidere.util.ParcelUtils;
import org.mariotaku.twidere.util.TwitterAPIFactory;
import org.mariotaku.twidere.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConversationLoader extends TwitterAPIStatusesLoader {

    @NonNull
    private final ParcelableStatus mStatus;
    private boolean mCanLoadAllReplies;

    public ConversationLoader(final Context context, @NonNull final ParcelableStatus status,
                              final String sinceId, final String maxId, final List<ParcelableStatus> data,
                              final boolean fromUser) {
        super(context, status.account_key, sinceId, maxId, data, null, -1, fromUser);
        mStatus = Nullables.assertNonNull(ParcelUtils.clone(status));
        ParcelableStatusUtils.makeOriginalStatus(mStatus);
    }

    @NonNull
    @Override
    public List<Status> getStatuses(@NonNull final Twitter twitter, @NonNull ParcelableCredentials credentials, @NonNull final Paging paging) throws TwitterException {
        mCanLoadAllReplies = false;
        final ParcelableStatus status = mStatus;
        if (Utils.isOfficialCredentials(getContext(), credentials)) {
            mCanLoadAllReplies = true;
            return twitter.showConversation(status.id, paging);
        } else if (TwitterAPIFactory.isStatusNetCredentials(credentials)) {
            mCanLoadAllReplies = true;
            return twitter.getStatusNetConversation(status.id, paging);
        }
        final List<Status> statuses = new ArrayList<>();
        final String maxId = getMaxId(), sinceId = getSinceId();
        final boolean noSinceMaxId = maxId == null && sinceId == null;
        // Load conversations
        if ((maxId != null && maxId < status.id) || noSinceMaxId) {
            String inReplyToId = maxId != null ? maxId : status.in_reply_to_status_id;
            int count = 0;
            while (inReplyToId != null && count < 10) {
                final Status item = twitter.showStatus(inReplyToId);
                inReplyToId = item.getInReplyToStatusId();
                statuses.add(item);
                count++;
            }
        }
        // Load replies
        if ((sinceId != null && sinceId > status.id) || noSinceMaxId) {
            SearchQuery query = new SearchQuery();
            if (TwitterAPIFactory.isTwitterCredentials(credentials)) {
                query.query("to:" + status.user_screen_name);
            } else {
                query.query("@" + status.user_screen_name);
            }
            query.sinceId(sinceId != null ? sinceId : status.id);
            try {
                for (Status item : twitter.search(query)) {
                    if (TextUtils.equals(item.getInReplyToStatusId(), status.id)) {
                        statuses.add(item);
                    }
                }
            } catch (TwitterException e) {
                // Ignore for now
            }
        }
        return statuses;
    }

    public boolean canLoadAllReplies() {
        return mCanLoadAllReplies;
    }

    @WorkerThread
    @Override
    protected boolean shouldFilterStatus(SQLiteDatabase database, ParcelableStatus status) {
        return InternalTwitterContentUtils.isFiltered(database, status, false);
    }

}
