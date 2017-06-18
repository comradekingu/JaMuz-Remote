package phramusca.com.jamuzremote;

/**
 * Created by raph on 17/06/17.
 */
public interface ICallBackPlayer {
    public void onPlayBackEnd();
    public void onPositionChanged(int position, int duration);
    public void onPlayRandom();
    public void onPlayBackStart();
}
