package phramusca.com.jamuzremote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * Created by raph on 03/03/18.
 */

public abstract class AdapterTrack extends AdapterLoad {

    private final Context mContext;
    private boolean complete;
    private boolean completeTop;
    TrackList trackList;

    AdapterTrack(Context context, List<Track> tracks, int positionPlaying, RecyclerView recyclerView) {
        super(context, recyclerView);
        mContext = context;
        trackList = new TrackList(tracks, positionPlaying);
        complete = false;
        completeTop = false;
        recyclerView.setAdapter(this);
        setOnLoadListener(new IListenerOnLoad() {
            @Override
            public void onLoadTop() {
                if (!completeTop) {
                    trackList.addLoaderTop();
                    notifyItemInserted(0);
                    new Handler().postDelayed(() -> {
                        completeTop = addTop() <= 0;
                        trackList.removeLoader(0);
                        notifyDataSetChanged();
                        setLoadedTop();
                        /*if(completeTop) {
                            Toast.makeText(mContext, "Top of list", Toast.LENGTH_SHORT).show();
                        }*/
                    }, 500);
                }
            }

            @Override
            public void onLoadMore() {
                if (!complete) {
                    int loaderPos = trackList.addLoader();
                    notifyItemInserted(loaderPos);
                    new Handler().post(() -> {
                        complete = !addMore();
                        trackList.removeLoader(loaderPos);
                        notifyDataSetChanged();
                        setLoaded();
                        /*if(complete) {
                            Toast.makeText(mContext, "End of list", Toast.LENGTH_SHORT).show();
                        }*/
                    });
                }
            }
        });
    }

    abstract List<Track> getMore();

    abstract List<Track> getTop();

    private boolean addMore() {
        List<Track> newTracks = getMore();
        this.trackList.addBottom(newTracks);
        return newTracks.size() > 0;
    }

    private int addTop() {
        List<Track> newTracks = getTop();
        this.trackList.addTop(newTracks);
        return newTracks.size();
    }

    @Override
    public int getItemViewType(int position) {
        return trackList.get(position) == null ? VIEW_TYPE_LOADING : VIEW_TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return trackList.size();
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof UserViewHolder) {
            UserViewHolder userViewHolder = (UserViewHolder) holder;
            Track track = trackList.get(position);
            track.getTags(false);
            setView(position, userViewHolder,
                    track.getTitle(),
                    track.getArtist(),
                    track.getAlbum(),
                    String.format(Locale.ENGLISH,
                            "%s %d/5 %s %s\n%s %s",
                            track.getTags(),
                            (int) track.getRating(),
                            track.getGenre(), track.getYear(), track.getLastPlayedAgo(), track.getAddedDateAgo()
                    ));

        } else if (holder instanceof LoadingViewHolder) {
            LoadingViewHolder loadingViewHolder = (LoadingViewHolder) holder;
            loadingViewHolder.progressBar.setIndeterminate(true);
        }
    }

    //FIXME NOW Merge with similar code in AdapterCursorAlbumTrack
    void setView(int position, UserViewHolder userViewHolder,
                 String line1, String line2, String line3, String line4) {
        if (trackList.get(position).getStatus().equals(Track.Status.INFO)) {
            userViewHolder.item_line1.setTextColor(ContextCompat.getColor(mContext, R.color.colorAccent));
            userViewHolder.item_line2.setTextColor(ContextCompat.getColor(mContext, R.color.colorAccent));
        } else if (trackList.get(position).getStatus().equals(Track.Status.ERROR)) {
            userViewHolder.item_line1.setTextColor(ContextCompat.getColor(mContext, R.color.lightYellow));
            userViewHolder.item_line2.setTextColor(ContextCompat.getColor(mContext, R.color.lightYellow));
        } else {
            userViewHolder.item_line1.setTextColor(ContextCompat.getColor(mContext, R.color.textColor));
            userViewHolder.item_line2.setTextColor(ContextCompat.getColor(mContext, R.color.textColor));
        }

        userViewHolder.item_line1.setText(line1);
        userViewHolder.item_line2.setText(line2);
        userViewHolder.item_line3.setText(line3);
        userViewHolder.item_line4.setText(line4);

        Bitmap bitmap = IconBufferCover.getCoverIcon(trackList.get(position), IconBufferCover.IconSize.THUMB, true);
        if (bitmap == null) {
            bitmap = HelperBitmap.getEmptyThumb();
        }
        if (position == trackList.getPositionPlaying()) {
            bitmap = overlayIcon(bitmap, R.drawable.ic_playing, mContext);
        }

        if (trackList.get(position).getStatus().equals(Track.Status.NEW)) {
            bitmap = overlayIcon(bitmap, R.drawable.ic_download, mContext);
        } else if (trackList.get(position).getStatus().equals(Track.Status.ERROR)) {
            bitmap = overlayIcon(bitmap, R.drawable.ic_error, mContext);
        }

        userViewHolder.imageViewCover.setImageBitmap(bitmap);

        if (trackList.get(position).isHistory()) {
            userViewHolder.layout_item.setBackgroundColor(ContextCompat.getColor(mContext, R.color.colorPrimary));
        } else {
            userViewHolder.layout_item.setBackgroundColor(ContextCompat.getColor(mContext, R.color.background_color));
        }

        userViewHolder.itemView.setTag(position);
        userViewHolder.itemView.setOnClickListener(view -> {
            Integer position1 = (Integer) view.getTag();
            sendListener(trackList.get(position1), position1);
        });
    }

    public static Bitmap overlayIcon(Bitmap bitmap, int iconId, Context context) {
        int margin = 15;
        Bitmap bmOverlay = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bitmap, new Matrix(), null); //TODO: Paint in black if cover is too much white
        Bitmap playingBitmap = BitmapFactory.decodeResource(context.getResources(), iconId);
        int newWidth = bmOverlay.getWidth() - (margin * 2);
        int newHeight = bmOverlay.getHeight() - (margin * 2);
        int width = playingBitmap.getWidth();
        int height = playingBitmap.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        matrix.postTranslate(margin, margin);
        canvas.drawBitmap(playingBitmap, matrix, null);
        return bmOverlay;
    }
}
