package refresco.streetnames

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.View


class HomeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val intent = Intent(this, ViewMapActivityGL20::class.java)
        startActivity(intent)

    }


    fun goToDownloadActivity(view: View) {
        val intent = Intent(this, DownloadActivity::class.java)
        startActivity(intent)
    }

    fun goToViewMapActivity(view: View) {
        val intent = Intent(this, ViewMapActivity::class.java)
        startActivity(intent)
    }
}
