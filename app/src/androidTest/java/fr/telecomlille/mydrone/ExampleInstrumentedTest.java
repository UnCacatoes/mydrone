package fr.telecomlille.mydrone;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import fr.telecomlille.mydrone.recognition.RecognitionActivity;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Rule
    public ActivityTestRule<RecognitionActivity> mActivityRule = new ActivityTestRule<RecognitionActivity>(RecognitionActivity.class) {
        @Override
        protected Intent getActivityIntent() {
            Context targetContext = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();
            Intent result = new Intent(targetContext, MainActivity.class);
            ARDiscoveryDeviceService mockService = mock(ARDiscoveryDeviceService.class);
            result.putExtra("DeviceService", mockService);
            return result;
        }
    };;

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("fr.telecomlille.mydrone", appContext.getPackageName());
    }
}
