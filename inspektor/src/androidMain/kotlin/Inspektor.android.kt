import android.content.Intent
import com.gyanoba.inspektor.MainActivity
import com.gyanoba.inspektor.utils.ContextInitializer

public actual fun openInspektor() {
    val context = ContextInitializer.appContext
    val intent = Intent().apply {
        setClass(context, MainActivity::class.java)
        setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}