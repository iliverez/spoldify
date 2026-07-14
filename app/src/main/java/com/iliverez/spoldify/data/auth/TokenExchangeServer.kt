package com.iliverez.spoldify.data.auth

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.URLEncoder

class TokenExchangeServer(
    private val context: Context,
    private val oauthUrl: String,
    private val onCodeReceived: (String) -> Unit
) : NanoHTTPD(PORT) {

    private var codeReceived = false

    fun startServer(): String? {
        val ip = getWifiIpAddress() ?: return null
        start()
        Log.i(TAG, "Token exchange server started at http://$ip:$PORT")
        return "http://$ip:$PORT"
    }

    fun isConnected(): Boolean = codeReceived

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/" || uri == "" -> serveSetupPage()
            uri == "/callback" -> handleCallback(session)
            uri == "/submit" -> handleSubmit(session)
            uri == "/status" -> serveStatus()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveSetupPage(): Response {
        val encodedUrl = URLEncoder.encode(oauthUrl, "UTF-8")
        val html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Spoldify Setup</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;text-align:center;padding:40px 20px;max-width:500px;margin:0 auto;background:#121212;color:#fff}
h1{color:#1DB954;margin-bottom:10px;font-size:28px}
p{color:#b3b3b3;margin:10px 0;line-height:1.5}
.btn{display:inline-block;background:#1DB954;color:#000;padding:16px 40px;border-radius:30px;text-decoration:none;font-size:18px;font-weight:600;margin:20px 0;border:none;cursor:pointer}
.fallback{margin-top:30px;padding:20px;background:#282828;border-radius:12px;text-align:left}
.fallback h3{color:#fff;margin-bottom:10px;font-size:16px}
.fallback p{font-size:13px;color:#a0a0a0}
input{width:100%;padding:12px;border:1px solid #535353;border-radius:8px;background:#3e3e3e;color:#fff;font-size:14px;margin:10px 0}
input::placeholder{color:#7a7a7a}
.submit{background:#535353;color:#fff;border:none;padding:12px 24px;border-radius:8px;font-size:14px;cursor:pointer;width:100%}
.status{margin-top:20px;padding:12px;border-radius:8px;display:none}
.success{background:#1a3a2a;color:#1DB954}
.error{background:#3a1a1a;color:#ff6b6b}
</style>
</head>
<body>
<h1>Spoldify</h1>
<p>Tap the button to connect with your Spotify account</p>
<a href="$oauthUrl" class="btn">Connect with Spotify</a>
<div class="fallback">
<h3>If the redirect doesn't work:</h3>
<p>After authorizing, copy the URL from your browser's address bar and paste it below:</p>
<input id="urlInput" type="text" placeholder="Paste URL here (spoldify://auth/callback?code=...)">
<button class="submit" onclick="submitCode()">Submit</button>
</div>
<div id="status" class="status"></div>
<script>
function submitCode(){
  var input=document.getElementById('urlInput').value.trim();
  var code=null;
  try{var url=new URL(input);code=url.searchParams.get('code')}
  catch(e){if(input.length>10)code=input}
  if(code){
    document.getElementById('status').className='status success';
    document.getElementById('status').style.display='block';
    document.getElementById('status').textContent='Connecting...';
    fetch('/callback?code='+encodeURIComponent(code))
      .then(function(r){
        if(r.ok){
          document.getElementById('status').textContent='Connected! You can close this page.';
        }else{
          document.getElementById('status').className='status error';
          document.getElementById('status').textContent='Error. Please try again.';
        }
      })
      .catch(function(e){
        document.getElementById('status').className='status error';
        document.getElementById('status').textContent='Error: '+e.message;
      });
  }else{
    document.getElementById('status').className='status error';
    document.getElementById('status').style.display='block';
    document.getElementById('status').textContent='Could not find the code. Paste the full URL.';
  }
}
setInterval(function(){
  fetch('/status').then(function(r){return r.text()}).then(function(t){
    if(t==='connected'){
      document.getElementById('status').className='status success';
      document.getElementById('status').style.display='block';
      document.getElementById('status').textContent='Connected! You can close this page.';
    }
  });
},2000);
</script>
</body>
</html>
""".trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handleCallback(session: IHTTPSession): Response {
        val code = session.parms["code"]
        if (code != null && !codeReceived) {
            codeReceived = true
            Log.i(TAG, "Received OAuth code via callback")
            onCodeReceived(code)
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:sans-serif;text-align:center;padding:40px;background:#121212;color:#fff}
h1{color:#1DB954;font-size:28px}p{color:#b3b3b3;margin:10px 0}</style></head>
<body><h1>Connected!</h1><p>Your Spoldify device is now set up.</p><p>You can close this page.</p></body></html>
""".trimIndent())
        }
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No code received")
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        val code = session.parms["code"]
        if (code != null && !codeReceived) {
            codeReceived = true
            Log.i(TAG, "Received OAuth code via submit")
            onCodeReceived(code)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, """
<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:sans-serif;text-align:center;padding:40px;background:#121212;color:#fff}
h1{color:#1DB954;font-size:28px}p{color:#b3b3b3;margin:10px 0}</style></head>
<body><h1>Connected!</h1><p>You can close this page.</p></body></html>
""".trimIndent())
    }

    private fun serveStatus(): Response {
        val status = if (codeReceived) "connected" else "waiting"
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, status)
    }

    @Suppress("DEPRECATION")
    private fun getWifiIpAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
    }

    companion object {
        private const val TAG = "TokenExchangeServer"
        const val PORT = 8888
    }
}
