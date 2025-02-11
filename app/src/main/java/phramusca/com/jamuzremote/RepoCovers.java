 /*
  * Copyright (C) 2021 phramusca ( https://github.com/phramusca/JaMuz/ )
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */

 package phramusca.com.jamuzremote;

 import android.graphics.Bitmap;
 import android.graphics.BitmapFactory;
 import android.util.Log;
 import android.util.Pair;

 import java.io.File;
 import java.io.FileOutputStream;
 import java.io.IOException;

 /**
  * @author phramusca ( https://github.com/phramusca/JaMuz/ )
  */
 public class RepoCovers {
     private static final String TAG = RepoCovers.class.getName();

     /**
      * Get cover icon from cache
      *
      * @param track The track.
      * @return The cover icon.
      */
     public static Bitmap getCoverIcon(Track track, IconSize iconSize, boolean readIfNotFound) {
         return getCoverIcon(track.getCoverHash(), track.getPath(), iconSize, readIfNotFound);
     }

     /**
      * Get cover icon from cache
      * @return The cover icon.
      */
     public static Bitmap getCoverIcon(String coverHash, String path, IconSize iconSize, boolean readIfNotFound) {
         Bitmap icon;
         icon = readIconFromCache(coverHash, iconSize);
         if (icon != null) {
             return icon;
         }
         if (readIfNotFound) {
             Bitmap trackCover = Track.readCover(path);
             Bitmap cover = writeIconToCache(coverHash, IconSize.COVER, trackCover);
             Bitmap thumb = writeIconToCache(coverHash, IconSize.THUMB, cover);
             if (iconSize == IconSize.COVER) {
                 icon = cover;
             } else if (iconSize == IconSize.THUMB) {
                 icon = thumb;
             }
         }
         return icon;
     }

     public static void writeIconToCache(String coverHash, Bitmap bitmap) {
         Bitmap cover = writeIconToCache(coverHash, IconSize.COVER, bitmap);
         writeIconToCache(coverHash, IconSize.THUMB, cover);
     }

     private static Bitmap writeIconToCache(String coverHash, IconSize iconSize, Bitmap cover) {
         Bitmap icon = null;
         if (cover != null) {
             Pair<Integer, Integer> scaledSize = new Pair<>(iconSize.size, iconSize.size);
             if (iconSize == IconSize.COVER) { //Not for THUMB or need to refactor AdapterTrack.overlayIcon(...)
                 scaledSize = iconSize.getScaledSize(cover.getWidth(), cover.getHeight());
             }
             icon = Bitmap.createScaledBitmap(cover, scaledSize.first, scaledSize.second, false);
             if (icon != null) {
                 try {
                     File cacheFile = getCacheFile(coverHash, iconSize);
                     FileOutputStream fOut = new FileOutputStream(cacheFile);
                     icon.compress(Bitmap.CompressFormat.PNG, 90, fOut);
                     fOut.flush();
                     fOut.close();
                 } catch (IOException e) {
                     Log.e(TAG, "Error writing thumb to cache", e); //NON-NLS
                     e.printStackTrace();
                 }
             }
         }
         return icon;
     }

     public static boolean contains(String coverHash, IconSize iconSize) {
         File file = getCacheFile(coverHash, iconSize);
         return file.exists();
     }

     //TODO: Offer at least a cache cleanup function (better would be a smart auto cleanup)
     //Until then, can delete cache folder (or only audio)
     public static Bitmap readIconFromCache(String coverHash, IconSize iconSize) {
         File file = getCacheFile(coverHash, iconSize);
         if (file.exists()) {
             return BitmapFactory.decodeFile(file.getAbsolutePath());
         }
         return null;
     }

     private static File getCacheFile(String coverHash, IconSize iconSize) {
         String filename = coverHash.equals("") ? "NA" : coverHash + iconSize.name();
         return getFile(filename + ".png", "..", "cache", "cover-icons"); //NON-NLS
     }

     //TODO: Move this to a generic class if to be used elsewhere
     private static File getFile(String filename, String... args) {
         File file = ActivityMain.getAppDataPath();
         for (String subFolder : args) {
             file = new File(file, subFolder);
             //noinspection ResultOfMethodCallIgnored
             file.mkdirs();
         }
         file = new File(file, filename);
         return file;
     }

     enum IconSize {
         COVER(500),
         THUMB(120);

         private final int size;

         IconSize(int size) {
             this.size = size;
         }

         private Pair<Integer, Integer> getScaledSize(int original_width, int original_height) {
             int bound_width = size;
             int bound_height = size;
             int new_width = original_width;
             int new_height = original_height;
             // first check if we need to scale width
             if (original_width > bound_width) {
                 //scale width to fit
                 new_width = bound_width;
                 //scale height to maintain aspect ratio
                 new_height = (new_width * original_height) / original_width;
             }
             // then check if we need to scale even with the new height
             if (new_height > bound_height) {
                 //scale height to fit instead
                 new_height = bound_height;
                 //scale width to maintain aspect ratio
                 new_width = (new_height * original_width) / original_height;
             }
             return new Pair<>(new_width, new_height);
         }

         public int getSize() {
             return size;
         }
     }
 }
