package moe.bemly.mrboto

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Core
    MRubyTest::class,
    ErrorHandlingTest::class,
    BridgeMethodsTest::class,
    RegistryStressTest::class,

    // Layout & Widgets
    LayoutConstantsTest::class,
    WidgetsTest::class,
    WidgetHierarchyTest::class,
    ViewInstanceMethodsTest::class,
    ViewPager2Test::class,
    WebViewTest::class,

    // Activity & Lifecycle
    ActivityClassTest::class,
    ActivityRegistrationTest::class,
    LifecycleDispatchTest::class,

    // Callbacks
    CallbackDispatchTest::class,

    // Helpers
    HelpersTest::class,
    ClipboardTest::class,
    FileOpsTest::class,
    FileEncodingTest::class,
    IntentExtrasTest::class,

    // Communication
    NetworkTest::class,
    HttpExTest::class,

    // System Integration
    PermissionTest::class,
    NotificationTest::class,
    SQLiteTest::class,
    ShellTest::class,
    IntentTest::class,
    PopupMenuCallbackTest::class,

    // Async & Threading
    CoroutineTest::class,
    ThreadingTest::class,

    // Accessibility
    AccessibilityTest::class,
    GestureTest::class,

    // Screen & Display
    ScreenCaptureTest::class,
    PredictiveBackTest::class,
    WindowInfoTest::class,

    // Overlay
    OverlayTest::class,

    // Color & Image
    ColorFindTest::class,
    ImageTest::class,

    // Events & Sensors
    EventListenerTest::class,
    SensorTest::class,

    // Device Control
    DeviceControlTest::class,

    // Camera
    CameraTest::class,

    // OCR
    OcrTest::class
)
class AllTests
