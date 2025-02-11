package phramusca.com.jamuzremote;

import static phramusca.com.jamuzremote.MusicLibraryDb.COL_STATUS;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by raph on 11/06/17.
 */
public class Playlist implements Comparable, Serializable {

    private static final String TAG = Playlist.class.getName();
    private String name;
    private final Map<String, TriStateButton.STATE> tags = new HashMap<>();
    private final Map<String, TriStateButton.STATE> genres = new HashMap<>();
    private TriStateButton.STATE unTaggedState = TriStateButton.STATE.ANY;
    private int rating = 0;
    private Operator ratingOperator = Playlist.Operator.GREATERTHAN;
    private final boolean isLocal;
    private String artist;
    private String album;
    private Order order = Order.PLAYCOUNTER_LASTPLAYED;
    private int limitValue = 0;
    private LimitUnit limitUnit = LimitUnit.MINUTES;
    private boolean modified = false;
    private int nbFiles = -1;

    Playlist(String name, boolean isLocal) {
        this.name = name;
        this.isLocal = isLocal;
    }

    public List<Track> getTracks(List<Track.Status> statuses) {
        return getTracks(-1, statuses);
    }

    public List<Track> getTracks(int limit, List<Track.Status> statuses) {
        return getTracks(limit, new ArrayList<>(), statuses);
    }

    public List<Track> getTracks(int limit, List<Integer> excluded, List<Track.Status> statuses) {
        if (HelperLibrary.musicLibrary != null) {
            return HelperLibrary.musicLibrary.getTracks(getWhere(excluded, statuses), getHaving(), order.value, limit);
        }
        return new ArrayList<>();
    }

    public Cursor getTracks() {
        if(HelperLibrary.musicLibrary!=null) {
            return HelperLibrary.musicLibrary.getTracksCursor(false, getWhere(new ArrayList<>(), new ArrayList<Track.Status>() {
                {
                    add(Track.Status.REC);
                    add(Track.Status.LOCAL);
                    add(Track.Status.INFO);
                    add(Track.Status.NEW);
                    add(Track.Status.ERROR);
                }
            }), getHaving(), order.value, -1);
        }
        return null;
    }

    public void getNbFiles() {
        if (HelperLibrary.musicLibrary != null) {
            Triplet<Integer, Long, Long> entry = HelperLibrary.musicLibrary.getNb(
                    getWhere(new ArrayList<>(), new ArrayList<Track.Status>() {
                        {
                            add(Track.Status.REC);
                            add(Track.Status.LOCAL);
                        }
                    }), getHaving());
            nbFiles = entry.getFirst();
            //TODO: Offer choice to display one or the other (length OR size)
            /*lengthOrSize = StringManager.humanReadableByteCount(entry.getSecond(), false);*/
            lengthOrSize = StringManager.humanReadableSeconds(entry.getThird(), "");
        }
    }

    public Set<Map.Entry<String, TriStateButton.STATE>> getTags() {
        return tags.entrySet();
    }

    private String getRatingString() {
        String in = "";
        switch (ratingOperator) {
            case GREATERTHAN:
                in += ">=";
                break;
            case IS:
                in += "=";
                break;
            case LESSTHAN:
                in += "<=";
                break;
        }
        in += rating;
        return in;
    }

    public String getSummary(Context context) {
        String in = " ";
        in += getRatingString();
        Lists tagsLists = new Lists();
        Lists genresLists = new Lists(genres);

        String nullStatus = "";
        switch (unTaggedState) {
            case TRUE:
                nullStatus = context.getString(R.string.playlistSummaryNullOnly);
                break;
            case FALSE:
                nullStatus = context.getString(R.string.playlistSummaryNullExcluded);
                tagsLists = new Lists(tags);
                break;
            case ANY:
                nullStatus = context.getString(R.string.playlistSummaryNullIncluded);
                tagsLists = new Lists(tags);
                break;
        }

        in += " | " + nullStatus;
        int max = 5; //TODO: Make it an option
        if (tagsLists.hasIncluded() || genresLists.hasIncluded()) {
            ArrayList<String> included = new ArrayList<>();
            if (tagsLists.hasIncluded()) {
                included.addAll(tagsLists.getIncluded());
            }
            included.addAll(genresLists.getIncluded());
            in += " | " + context.getString(R.string.playlistSummaryIncluded) + ": " + getString(included, max);
        }

        if (tagsLists.hasExcluded() || genresLists.hasExcluded()) {
            ArrayList<String> excluded = new ArrayList<>();
            if (tagsLists.hasIncluded()) {
                excluded.addAll(tagsLists.getExcluded());
            }
            excluded.addAll(genresLists.getExcluded());
            in += " | " + context.getString(R.string.playlistSummaryExcluded) + ": " + getString(excluded, max);
        }

        in += " | " + order.getDisplay(context);
        in += " | " + (limitValue > 0 ? limitValue + " " + limitUnit.getDisplay(context) : "");

        return in;
    }

    public Set<Map.Entry<String, TriStateButton.STATE>> getGenres() {
        return genres.entrySet();
    }

    public void setLimitUnit(LimitUnit limitUnit) {
        this.limitUnit = limitUnit;
        modified = true;
    }

    public LimitUnit getLimitUnit() {
        return limitUnit;
    }

    public int getLimitValue() {
        return limitValue;
    }

    public void setLimitValue(int limitValue) {
        this.limitValue = limitValue;
        modified = true;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    private static class Lists {
        ArrayList<String> include = new ArrayList<>();
        ArrayList<String> exclude = new ArrayList<>();

        Lists() {
        }

        Lists(Map<String, TriStateButton.STATE> stateMap) {
            if (stateMap.size() > 0) {
                for (Map.Entry<String, TriStateButton.STATE> entry : stateMap.entrySet()) {
                    switch (entry.getValue()) {
                        case FALSE:
                            exclude.add(entry.getKey());
                            break;
                        case TRUE:
                            include.add(entry.getKey());
                            break;
                    }
                }
            }
        }

        ArrayList<String> getIncluded() {
            return include;
        }

        ArrayList<String> getExcluded() {
            return exclude;
        }

        boolean hasIncluded() {
            return include.size() > 0;
        }

        boolean hasExcluded() {
            return exclude.size() > 0;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private String getString(ArrayList<String> strings, int max) {
        StringBuilder out = new StringBuilder();
        if (strings.size() > 0) {
            for (int i = 0; i < max; i++) {
                if (strings.size() > i) {
                    out.append(strings.get(i)).append(", ");
                }
            }
            out = new StringBuilder(out.substring(0, out.length() - 2));
            if (strings.size() > max) {
                out.append(" (+").append(strings.size() - max).append(")");
            }
        }
        return out.toString();
    }

    public TriStateButton.STATE getUnTaggedState() {
        return unTaggedState;
    }

    public int getRating() {
        return rating;
    }

    public String getRatingOperator() {
        return ratingOperator.toString();
    }

    public void toggleTag(String value, TriStateButton.STATE state) {
        if (value.equals("null")) { //NON-NLS
            unTaggedState = state;
        } else {
            tags.put(value, state);
        }
        modified = true;
    }

    public void toggleGenre(String value, TriStateButton.STATE state) {
        genres.put(value, state);
        modified = true;
    }

    /**
     * Rotates rating operator
     * : ">" to "=" to "<" to ">" and so on
     *
     * @return new rating operator
     */
    public String setRatingOperator() {
        switch (ratingOperator) {
            case GREATERTHAN:
                ratingOperator = Operator.IS;
                break;
            case IS:
                ratingOperator = Operator.LESSTHAN;
                break;
            case LESSTHAN:
                ratingOperator = Operator.GREATERTHAN;
                break;
        }
        modified = true;
        return ratingOperator.toString();
    }

    public void setRating(int rating) {
        this.rating = rating;
        modified = true;
    }

    private String getWhere(List<Integer> excluded, List<Track.Status> statuses) {
        ArrayList<String> statusString = new ArrayList<>();
        for(Track.Status status : statuses) {
            statusString.add(status.name());
        }
        String in = " WHERE " + COL_STATUS + " IN ( " + getInClause(statusString) + " )" + //NON-NLS
                " AND rating " + getRatingString() + " "; //NON-NLS

        if (limitValue > 0) {
            in += "\n AND lastPlayed < datetime(datetime('now'), '-" + limitValue + " " + limitUnit.value + "')"; //NON-NLS
        }

        ArrayList<String> include = new ArrayList<>();
        ArrayList<String> exclude = new ArrayList<>();
        for (Map.Entry<String, TriStateButton.STATE> entry : genres.entrySet()) {
            switch (entry.getValue()) {
                case FALSE:
                    exclude.add(entry.getKey());
                    break;
                case TRUE:
                    include.add(entry.getKey());
                    break;
            }
        }

        if (include.size() > 0) {
            in += "\n AND genre IN (" + getInClause(include) + ") "; //NON-NLS
        }
        if (exclude.size() > 0) {
            in += "\n AND genre NOT IN (" + getInClause(exclude) + ") "; //NON-NLS
        }

        if (artist != null) {
            in += "\n AND artist LIKE \"" + artist + "\" "; //NON-NLS
        }

        if (album != null) {
            in += "\n AND album LIKE \"" + album + "\" "; //NON-NLS
        }

        in += getInClause(excluded);

        return in;
    }

    private String getHaving() {
        //FILTER by TAGS
        String in;
        if (unTaggedState.equals(TriStateButton.STATE.TRUE)) {
            in = " HAVING tag.value IS NULL "; //NON-NLS
        } else {
            //Include or exclude tags according to states
            in = " HAVING ( "; //NON-NLS
            if (tags.size() > 0) {
                ArrayList<String> include = new ArrayList<>();
                ArrayList<String> exclude = new ArrayList<>();
                for (Map.Entry<String, TriStateButton.STATE> entry : tags.entrySet()) {
                    switch (entry.getValue()) {
                        case FALSE:
                            exclude.add(entry.getKey());
                            break;
                        case TRUE:
                            include.add(entry.getKey());
                            break;
                    }
                }
                in += getInClause(include, include.size());
                in += "\n AND " + getInClause(exclude, 0); //NON-NLS
            } else {
                in += " 1 "; //NON-NLS
            }
            in += " ) "; //NON-NLS
            //Include or exclude untagged
            if (unTaggedState.equals(TriStateButton.STATE.ANY)) {
                in += "\n OR tag.value IS NULL "; //NON-NLS
            } else if (unTaggedState.equals(TriStateButton.STATE.FALSE)) {
                in += "\n AND tag.value IS NOT NULL "; //NON-NLS
            }
        }
        return in;
    }

    private static String getInClause(List<Integer> excluded) {
        StringBuilder builder = new StringBuilder();
        if (excluded.size() > 0) {
            builder.append("\n AND tracks.idFileRemote NOT IN ("); //NON-NLS
            for (int integer : excluded) {
                builder.append(integer).append(",");
            }
            builder.deleteCharAt(builder.length() - 1).append(") ");
        }
        return builder.toString();
    }

    private String getInClause(ArrayList<String> include) {
        StringBuilder in = new StringBuilder();
        for (String entry : include) {
            in.append("\"").append(entry).append("\",");
        }
        return in.substring(0, in.length() - 1);
    }

    private String getInClause(ArrayList<String> include, int length) {
        StringBuilder in = new StringBuilder();
        if (include.size() > 0) {
            in.append(" sum(case when tag.value IN ("); //NON-NLS
            for (String entry : include) {
                in.append("\"").append(entry).append("\","); //NON-NLS
            }
            in = new StringBuilder(in.substring(0, in.length() - 1));
            in.append(" ) then 1 else 0 end) = ").append(length); //NON-NLS
        } else {
            in.append(" 1 "); //NON-NLS
        }
        return in.toString();
    }

    public String getName() {
        return name;
    }

    private String lengthOrSize;

    public void resetNbFilesAndLengthOrSize() {
        nbFiles = 0;
        lengthOrSize = " ";
    }

    @Override
    public String toString() {
        return isLocal ?
                name + " (" + nbFiles + " | " + lengthOrSize + ")"
                : name;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        return (this.name.compareTo(((Playlist) o).name));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Playlist other = (Playlist) obj;
        return Objects.equals(this.name, other.name);
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setAlbum(String album) {
        this.album = album;
        this.order = Order.DISC_TRACK;
    }

    /**
     * Operator for filters
     */
    public enum Operator {
        IS("="), //NOI18N
        LESSTHAN("<="), //NOI18
        GREATERTHAN(">="); //NOI18N

        private final String display;

        Operator(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
        modified = true;
    }

    public enum LimitUnit {
        @SerializedName("minutes") //NON-NLS
        MINUTES("minutes", 0), //NON-NLS
        @SerializedName("hours") //NON-NLS
        HOURS("hours", 1), //NON-NLS
        @SerializedName("days") //NON-NLS
        DAYS("days", 2), //NON-NLS
        @SerializedName("months") //NON-NLS
        MONTHS("months", 3), //NON-NLS
        @SerializedName("years") //NON-NLS
        YEARS("years", 4); //NON-NLS

        private final String value;
        private final int index;

        LimitUnit(String value, int index) {
            this.value = value;
            this.index = index;
        }

        public String getDisplay(Context context) {
            return context.getResources().getStringArray(R.array.limitUnits)[index];
        }
    }

    public enum Order {
        RANDOM("ORDER BY RANDOM()", R.string.playlistOrderRandom),  //NON-NLS
        PLAYCOUNTER_LASTPLAYED("ORDER BY playCounter, lastPlayed", R.string.playlistOrderPlayCounter), //NON-NLS
        DISC_TRACK("ORDER BY discNo, trackNo", R.string.playlistOrderTrackNb);  //NON-NLS

        private final String value;
        private final int resId;

        Order(String value, int resId) {
            this.value = value;
            this.resId = resId;
        }

        public String getDisplay(Context context) {
            return context.getString(resId);
        }
    }

    public boolean save(String folder) {
        Gson gson = new Gson();
        boolean previousModified = modified;
        modified = false; //otherwise saved as modified => non-sense
        if (HelperFile.write(folder, getName() + ".plli", gson.toJson(this))) {
            return true;
        } else {
            modified = previousModified;
            return false;
        }
    }
}
