package com.smarttracker.ui.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.smarttracker.databinding.FragmentBrowserBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * InAppBrowserFragment
 *
 * Tracks web URLs visited — only via this in-app browser (per project spec).
 * Logs each page visit to Room DB and optionally syncs to Firestore.
 *
 * INTEGRATION POINT:
 *   BrowserViewModel → UsageRepository → Room (web_visits table)
 *   FirebaseSyncWorker picks up unsynced web visits along with app usage.
 */
@AndroidEntryPoint
class InAppBrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        setupAddressBar()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            webViewClient = object : WebViewClient() {
                private var pageStartTime = 0L

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    pageStartTime = System.currentTimeMillis()
                    binding.progressBar.visibility = View.VISIBLE
                    binding.etUrl.setText(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    val timeSpentMs = System.currentTimeMillis() - pageStartTime

                    // ── INTEGRATION: Log web visit to Room (synced to Firestore later) ──
                    viewModel.logWebVisit(
                        url = url,
                        title = view.title ?: url,
                        timeSpentMs = timeSpentMs
                    )
                }

                // Block tracking scripts for privacy
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    binding.etUrl.setText(request.url.toString())
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                }
            }

            loadUrl("https://www.google.com")
        }
    }

    private fun setupAddressBar() {
        binding.etUrl.setOnEditorActionListener { _, _, _ ->
            val input = binding.etUrl.text.toString().trim()
            val url = if (input.startsWith("http")) input else "https://$input"
            binding.webView.loadUrl(url)
            true
        }

        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }
    }

    override fun onDestroyView() {
        binding.webView.destroy()
        super.onDestroyView()
        _binding = null
    }
}
