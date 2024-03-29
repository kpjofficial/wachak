package de.test.antennapod.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.ActivityInstrumentationTestCase2;
import android.test.FlakyTest;
import android.widget.ListView;

import com.robotium.solo.Solo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.activity.OnlineFeedViewActivity;
import de.voicehired.wachak.core.feed.Feed;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.core.storage.PodDBAdapter;
import de.voicehired.wachak.fragment.DownloadsFragment;
import de.voicehired.wachak.fragment.EpisodesFragment;
import de.voicehired.wachak.fragment.PlaybackHistoryFragment;
import de.voicehired.wachak.fragment.QueueFragment;
import de.voicehired.wachak.preferences.PreferenceController;

/**
 * User interface tests for MainActivity
 */
public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private Solo solo;
    private UITestUtils uiTestUtils;

    private SharedPreferences prefs;

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        uiTestUtils = new UITestUtils(getInstrumentation().getTargetContext());
        uiTestUtils.setup();

        // create new database
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        // override first launch preference
        // do this BEFORE calling getActivity()!
        prefs = getInstrumentation().getTargetContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(MainActivity.PREF_IS_FIRST_LAUNCH, false).commit();

        solo = new Solo(getInstrumentation(), getActivity());
    }

    @Override
    protected void tearDown() throws Exception {
        uiTestUtils.tearDown();
        solo.finishOpenedActivities();

        PodDBAdapter.deleteDatabase();

        // reset preferences
        prefs.edit().clear().commit();

        super.tearDown();
    }

    private void openNavDrawer() {
        solo.clickOnImageButton(0);
        getInstrumentation().waitForIdleSync();
    }

    public void testAddFeed() throws Exception {
        uiTestUtils.addHostedFeedData();
        final Feed feed = uiTestUtils.hostedFeeds.get(0);
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.add_feed_label));
        solo.enterText(0, feed.getDownload_url());
        solo.clickOnButton(solo.getString(R.string.confirm_label));
        solo.waitForActivity(OnlineFeedViewActivity.class);
        solo.waitForView(R.id.butSubscribe);
        assertEquals(solo.getString(R.string.subscribe_label), solo.getButton(0).getText().toString());
        solo.clickOnButton(0);
        solo.waitForText(solo.getString(R.string.subscribed_label));
    }

    @FlakyTest(tolerance = 3)
    public void testClickNavDrawer() throws Exception {
        uiTestUtils.addLocalFeedData(false);

        UserPreferences.setHiddenDrawerItems(new ArrayList<String>());

        // queue
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.queue_label));
        solo.waitForView(android.R.id.list);
        assertEquals(solo.getString(R.string.queue_label), getActionbarTitle());

        // episodes
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.episodes_label));
        solo.waitForView(android.R.id.list);
        assertEquals(solo.getString(R.string.episodes_label), getActionbarTitle());

        // downloads
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.downloads_label));
        solo.waitForView(android.R.id.list);
        assertEquals(solo.getString(R.string.downloads_label), getActionbarTitle());

        // playback history
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.playback_history_label));
        solo.waitForView(android.R.id.list);
        assertEquals(solo.getString(R.string.playback_history_label), getActionbarTitle());

        // add podcast
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.add_feed_label));
        solo.waitForView(R.id.txtvFeedurl);
        assertEquals(solo.getString(R.string.add_feed_label), getActionbarTitle());

        // podcasts
        ListView list = (ListView) solo.getView(R.id.nav_list);
        for (int i = 0; i < uiTestUtils.hostedFeeds.size(); i++) {
            Feed f = uiTestUtils.hostedFeeds.get(i);
            solo.clickOnScreen(50, 50); // open nav drawer
            solo.scrollListToLine(list, i);
            solo.clickOnText(f.getTitle());
            solo.waitForView(android.R.id.list);
            assertEquals("", getActionbarTitle());
        }
    }

    private String getActionbarTitle() {
        return ((MainActivity) solo.getCurrentActivity()).getSupportActionBar().getTitle().toString();
    }

    @SuppressWarnings("unchecked")
    @FlakyTest(tolerance = 3)
    public void testGoToPreferences() {
        openNavDrawer();
        solo.clickOnText(solo.getString(R.string.settings_label));
        solo.waitForActivity(PreferenceController.getPreferenceActivity());
    }

    public void testDrawerPreferencesHideSomeElements() {
        UserPreferences.setHiddenDrawerItems(new ArrayList<String>());
        openNavDrawer();
        solo.clickLongOnText(solo.getString(R.string.queue_label));
        solo.waitForDialogToOpen();
        solo.clickOnText(solo.getString(R.string.episodes_label));
        solo.clickOnText(solo.getString(R.string.playback_history_label));
        solo.clickOnText(solo.getString(R.string.confirm_label));
        solo.waitForDialogToClose();
        List<String> hidden = UserPreferences.getHiddenDrawerItems();
        assertEquals(2, hidden.size());
        assertTrue(hidden.contains(EpisodesFragment.TAG));
        assertTrue(hidden.contains(PlaybackHistoryFragment.TAG));
    }

    public void testDrawerPreferencesUnhideSomeElements() {
        List<String> hidden = Arrays.asList(PlaybackHistoryFragment.TAG, DownloadsFragment.TAG);
        UserPreferences.setHiddenDrawerItems(hidden);
        openNavDrawer();
        solo.clickLongOnText(solo.getString(R.string.queue_label));
        solo.waitForDialogToOpen();
        solo.clickOnText(solo.getString(R.string.downloads_label));
        solo.clickOnText(solo.getString(R.string.queue_label));
        solo.clickOnText(solo.getString(R.string.confirm_label));
        solo.waitForDialogToClose();
        hidden = UserPreferences.getHiddenDrawerItems();
        assertEquals(2, hidden.size());
        assertTrue(hidden.contains(QueueFragment.TAG));
        assertTrue(hidden.contains(PlaybackHistoryFragment.TAG));
    }

    public void testDrawerPreferencesHideAllElements() {
        UserPreferences.setHiddenDrawerItems(new ArrayList<String>());
        String[] titles = getInstrumentation().getTargetContext().getResources().getStringArray(R.array.nav_drawer_titles);

        openNavDrawer();
        solo.clickLongOnText(solo.getString(R.string.queue_label));
        solo.waitForDialogToOpen();
        for (String title : titles) {
            solo.clickOnText(title);
        }
        solo.clickOnText(solo.getString(R.string.confirm_label));
        solo.waitForDialogToClose();
        List<String> hidden = UserPreferences.getHiddenDrawerItems();
        assertEquals(titles.length, hidden.size());
        for (String tag : MainActivity.NAV_DRAWER_TAGS) {
            assertTrue(hidden.contains(tag));
        }
    }

    public void testDrawerPreferencesHideCurrentElement() {
        UserPreferences.setHiddenDrawerItems(new ArrayList<String>());

        openNavDrawer();
        String downloads = solo.getString(R.string.downloads_label);
        solo.clickOnText(downloads);
        solo.waitForView(android.R.id.list);
        openNavDrawer();
        solo.clickLongOnText(downloads);
        solo.waitForDialogToOpen();
        solo.clickOnText(downloads);
        solo.clickOnText(solo.getString(R.string.confirm_label));
        solo.waitForDialogToClose();
        List<String> hidden = UserPreferences.getHiddenDrawerItems();
        assertEquals(1, hidden.size());
        assertTrue(hidden.contains(DownloadsFragment.TAG));
    }
}
