package phramusca.com.jamuzremote;

import android.media.MediaMetadataRetriever;

import java.io.File;

/**
 * Created by raph on 01/05/17.
 */
public class Track {
    private int id;
    private int rating;
    private String title;
    private String album;
    private String artist;
    private String coverHash;
    private String path;
    private String genre;

    public Track(int id, int rating, String title, String album,
                 String artist, String coverHash, String path, String genre) {
        this.id = id;
        this.rating = rating;
        this.title = title;
        this.album = album;
        this.artist = artist;
        this.coverHash = coverHash;
        this.genre=genre;
        this.path = path;
    }

    @Override
    public String toString() {
        return   title + "<BR/>" +
                artist + "<BR/>"+
                album + "<BR/>"+
                genre + "<BR/>";
    }

    public int getId() {
        return id;
    }

    public String getCoverHash() {
        return coverHash;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public String getAlbum() {
        return album;
    }

    public String getArtist() {
        return artist;
    }

    public String getGenre() {
        return genre;
    }

    public String getPath() {
        return path;
    }

    //TODO: Use the same cache system as for remote
    public byte[] getArt() {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(path);
        byte[] art = mmr.getEmbeddedPicture();
        return art;
    }
}