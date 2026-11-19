/*
 * SkyTube
 * Copyright (C) 2018  Ramon Mifsud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.gui.businessobjects.adapters;

import java.util.List;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.Iterator;

import free.rm.skytube.R;
import free.rm.skytube.app.EventBus;
import free.rm.skytube.businessobjects.YouTube.POJOs.ChannelView;
import free.rm.skytube.businessobjects.YouTube.newpipe.ChannelId;
import free.rm.skytube.businessobjects.db.DatabaseTasks;
import free.rm.skytube.databinding.SubChannelBinding;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Channel subscriptions adapter: Contains a list of channels (that the user subscribed to) together
 * with a notification whether the channel has new videos since last visit to the channel or not.
 */
public class SubsAdapter extends RecyclerViewAdapterEx<ChannelView, SubsAdapter.SubChannelViewHolder> {

	private static final String TAG = SubsAdapter.class.getSimpleName();

	private String searchText;

	private final CompositeDisposable compositeDisposable = new CompositeDisposable();

	public SubsAdapter(Context context, View progressBar) {
		super(context);

		// populate this adapter with user's subscribed channels
		executeQuery(null, progressBar);
	}

	/**
	 * Remove channel from this adapter.
	 *
	 * @param channelId Channel to remove.
	 */
	public void removeChannel(ChannelId channelId) {
		int size = getItemCount();

		for (int i = 0; i < size; i++) {
			if (get(i).getId().equals(channelId)) {
				remove(i);
				return;
			}
		}

		Log.e(TAG, "Channel not removed from adapter:  id=" + channelId);
	}

	/**
	 * Changes the channel's 'new videos' status.  The channel's view is then refreshed.
	 *
	 * @param channelId Channel ID.
	 * @param newVideos 'New videos' status (true = new videos have been added since user's last
	 *                  visit;  false = no new videos)
	 * @return True if the operations have been successful; false otherwise.
	 */
	public boolean changeChannelNewVideosStatus(ChannelId channelId, boolean newVideos) {
		ChannelView channel;
		int position = 0;

		for (Iterator<ChannelView> i = getIterator(); i.hasNext(); position++) {
			channel = i.next();

			if (channel.getId() != null && channel.getId().equals(channelId)) {
				// change the 'new videos' status
				channel.setNewVideosSinceLastVisit(newVideos);
				// we now need to notify the SubsAdapter to remove the new videos notification (near the channel name)
				updateView(position);
				return true;
			}
		}

		return false;
	}

	/**
	 * Update the contents of a view (i.e. refreshes the given view).
	 *
	 * @param viewPosition The position of the view that we want to update.
	 */
	private void updateView(int viewPosition) {
		notifyItemChanged(viewPosition);
	}

	@Override
	@NonNull
	public SubChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		SubChannelBinding binding = SubChannelBinding.inflate(LayoutInflater.from(parent.getContext()),
				parent, false);
		return new SubChannelViewHolder(binding);
	}

	@Override
	public void onBindViewHolder(SubChannelViewHolder viewHolder, int position) {
		viewHolder.updateInfo(get(position));
	}

	/**
	 * This should be called only from MainFragment
	 */
	public void refreshSubsList() {
		clearList();
		executeQuery(searchText, null);
	}

	private void refreshFilteredSubsList(String searchText) {
		clearList();
		executeQuery(searchText, null);
	}

	private void executeQuery(String searchText, View progressBar) {
		compositeDisposable.add(DatabaseTasks.getSubscribedChannelView(getContext(), progressBar, searchText)
				.subscribe(this::appendUnique));
	}

	/** Two refreshes can overlap; never show the same channel twice. */
	private void appendUnique(List<ChannelView> channels) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < getItemCount(); i++) {
			seen.add(get(i).getId().getRawId());
		}
		List<ChannelView> fresh = new java.util.ArrayList<>();
		for (ChannelView c : channels) {
			if (seen.add(c.getId().getRawId())) fresh.add(c);
		}
		appendList(fresh);
	}

	public void filterSubSearch(String searchText){
		this.searchText = searchText;
		refreshFilteredSubsList(searchText);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	class SubChannelViewHolder extends RecyclerView.ViewHolder {
		private final SubChannelBinding binding;
		private ChannelView channel;

		SubChannelViewHolder(SubChannelBinding binding) {
			super(binding.getRoot());
			this.binding = binding;
			binding.getRoot().setOnClickListener(v -> {
				ChannelId channelId = channel.getId();
				EventBus.getInstance().notifyMainActivities(listener -> {
					listener.onChannelClick(channelId);
				});
			});
		}

		void updateInfo(ChannelView channel) {
			// Channel pictures come from a different server that some networks/filters block, so SafeTube
			// draws its own round letter picture instead (always works, nothing to download).
			binding.subChannelThumbnailImageView.setImageDrawable(new LetterAvatar(channel.getTitle()));

            final String prefix;
            switch (channel.status()) {
                case ACCOUNT_TERMINATED: {
                    prefix = itemView.getContext().getString(R.string.status_account_terminated);
                    break;
                }
                case NOT_EXISTS: {
                    prefix = itemView.getContext().getString(R.string.status_not_exists);
                    break;
                }
                default: {
                    prefix = "";
                    break;
                }
            }
			binding.subChannelNameTextView.setText(prefix + channel.getTitle());
			binding.subChannelNewVideosNotification.setVisibility(channel.isNewVideosSinceLastVisit() ? View.VISIBLE : View.INVISIBLE);
			this.channel = channel;
		}
	}

	/** Round coloured circle with the channel's first letter. */
	static final class LetterAvatar extends android.graphics.drawable.Drawable {
		private static final int[] COLORS = {0xFF1E6F8C, 0xFF7A4EAB, 0xFF2E8B57, 0xFFB5651D, 0xFFA23B5B, 0xFF4B6584};
		private final android.graphics.Paint circle = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private final String letter;

		LetterAvatar(String title) {
			String t = title == null ? "" : title.trim();
			letter = t.isEmpty() ? "?" : t.substring(0, t.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
			circle.setColor(COLORS[Math.abs(t.hashCode()) % COLORS.length]);
			text.setColor(0xFFFFFFFF);
			text.setTextAlign(android.graphics.Paint.Align.CENTER);
			text.setFakeBoldText(true);
		}

		@Override
		public void draw(android.graphics.Canvas c) {
			android.graphics.Rect r = getBounds();
			float rad = Math.min(r.width(), r.height()) / 2f;
			c.drawCircle(r.exactCenterX(), r.exactCenterY(), rad, circle);
			text.setTextSize(rad * 1.1f);
			float y = r.exactCenterY() - (text.descent() + text.ascent()) / 2f;
			c.drawText(letter, r.exactCenterX(), y, text);
		}

		@Override public void setAlpha(int a) { circle.setAlpha(a); }
		@Override public void setColorFilter(android.graphics.ColorFilter f) { circle.setColorFilter(f); }
		@Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
	}
}
