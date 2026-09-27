package com.bittv.iptv.market

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.R
import com.bittv.iptv.ads.AdManager
import com.bittv.iptv.util.CloudEconomyManager
import com.bittv.iptv.util.UiPolish
import com.bittv.iptv.util.VirtualMarketManager
import java.util.Locale

/** Virtual stock market UI backed by the server-authoritative economy functions. */
class VirtualMarketActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var walletText: TextView
    private lateinit var portfolioText: TextView
    private lateinit var statusText: TextView
    private var tradeQuantity = 1
    private var lastQuotes: List<CloudEconomyManager.MarketQuote> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 24)
            setBackgroundColor(getColor(R.color.bg_root))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "📈 PASAR VIRTUAL"
            textSize = 24f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        walletText = TextView(this).apply {
            textSize = 15f
            setTextColor(getColor(R.color.game_accent_light))
        }
        portfolioText = TextView(this).apply {
            text = "Portfolio: memuat…"
            textSize = 12f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 6, 0, 2)
        }
        statusText = TextView(this).apply {
            text = "Harga dan transaksi virtual. Tidak ada uang nyata, cash-out, atau nilai investasi riil."
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
        }
        header.addView(title)
        header.addView(walletText)
        header.addView(portfolioText)
        header.addView(statusText)
        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        UiPolish.setupEdgeToEdge(this, root)
        UiPolish.polish(root)

        AdManager.initialize(this)
        CloudEconomyManager.bootstrap(this) { result ->
            runOnUiThread {
                result.onSuccess { wallet -> walletText.text = "💰 ${wallet.coins} RAPO Coin • Streak ${wallet.streak}" }
                result.onFailure { statusText.text = "Backend ekonomi belum aktif: ${it.message ?: "error"} • harga lokal tetap tersedia" }
                loadMarket(content)
                refreshPortfolio()
            }
        }
    }

    private fun loadMarket(content: LinearLayout) {
        val quotes = listOf(
            Triple("BITV", "BITTV Network", "Media"),
            Triple("RAPO", "RAPO Digital", "Tech"),
            Triple("NUSA", "Nusantara Cloud", "Cloud"),
            Triple("LUME", "Lumen Studio", "Creative"),
            Triple("KOTA", "Kota Mart", "Retail"),
            Triple("ARCA", "Arca Energy", "Energy"),
            Triple("JAYA", "Jaya Food", "Food"),
            Triple("ORBI", "Orbit Labs", "Science")
        )
        content.removeAllViews()

        val quantityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 10)
        }
        quantityRow.addView(TextView(this).apply {
            text = "Jumlah:"
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        listOf(1, 5, 10).forEach { amount ->
            val q = Button(this).apply {
                text = "$amount×"
                isAllCaps = false
                isSelected = amount == tradeQuantity
                setOnClickListener {
                    tradeQuantity = amount
                    loadMarket(content)
                }
            }
            quantityRow.addView(q, LinearLayout.LayoutParams(64, -2).apply { leftMargin = 4 })
        }
        content.addView(quantityRow)

        quotes.forEach { (symbol, fallbackName, fallbackSector) ->
            val card = LinearLayout(this).apply {
                tag = symbol
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                setBackgroundResource(R.drawable.bg_button_game)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
            }
            val nameText = TextView(this).apply {
                text = "$symbol  •  $fallbackName"
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.text_primary))
            }
            val sectorText = TextView(this).apply {
                text = "Sektor $fallbackSector"
                textSize = 11f
                setTextColor(getColor(R.color.text_secondary))
            }
            val priceText = TextView(this).apply { tag = "price_$symbol"; textSize = 14f; setTextColor(getColor(R.color.game_accent_light)) }
            val holdingText = TextView(this).apply { textSize = 11f; setTextColor(getColor(R.color.text_secondary)) }
            val controls = LinearLayout(this).apply { gravity = Gravity.END; orientation = LinearLayout.HORIZONTAL }
            val buy = Button(this).apply { text = "Beli $tradeQuantity"; isAllCaps = false }
            val sell = Button(this).apply { text = "Jual $tradeQuantity"; isAllCaps = false }
            controls.addView(buy)
            controls.addView(sell)
            card.addView(nameText)
            card.addView(sectorText)
            card.addView(priceText)
            card.addView(holdingText)
            card.addView(controls)
            content.addView(card)

            val quote = VirtualMarketManager.quotes().firstOrNull { it.symbol == symbol }
            priceText.text = "Harga lokal ${quote?.price ?: "-"} • ${String.format(Locale.US, "%+.1f%%", quote?.changePct ?: 0.0)}"
            buy.setOnClickListener { trade(symbol, "BUY", priceText) }
            sell.setOnClickListener { trade(symbol, "SELL", priceText) }
        }

        val refresh = Button(this).apply {
            text = "↻ Refresh Market & Portfolio"
            isAllCaps = false
            setOnClickListener { loadMarket(content); refreshPortfolio() }
        }
        content.addView(refresh)

        CloudEconomyManager.getMarket(this) { result ->
            runOnUiThread {
                result.onSuccess { serverQuotes ->
                    lastQuotes = serverQuotes
                    serverQuotes.forEach { q ->
                        val card = content.findViewWithTag<LinearLayout>(q.symbol)
                        val label = card?.findViewWithTag<TextView>("price_${q.symbol}")
                        label?.text = "Harga server ${q.price} • ${String.format(Locale.US, "%+.2f%%", q.changePct)}"
                    }
                    statusText.text = "Market tersinkron server • ${serverQuotes.size} instrumen"
                    refreshPortfolio()
                }.onFailure { statusText.text = "Market lokal aktif • server belum merespons." }
            }
        }
        AdManager.attachBanner(this, content)
        UiPolish.polish(content)
        UiPolish.polish(root)
    }

    private fun refreshPortfolio() {
        CloudEconomyManager.getPortfolio(this) { result ->
            runOnUiThread {
                result.onSuccess { portfolio ->
                    walletText.text = "💰 ${portfolio.coins} RAPO Coin"
                    renderPortfolio(portfolio)
                }.onFailure { statusText.text = "Portfolio server belum tersedia • mode lokal aktif." }
            }
        }
    }

    private fun renderPortfolio(portfolio: CloudEconomyManager.Portfolio) {
        val quotes = if (lastQuotes.isNotEmpty()) lastQuotes else VirtualMarketManager.quotes().map {
            CloudEconomyManager.MarketQuote(it.symbol, it.name, it.sector, it.price.toLong(), it.changePct)
        }
        var marketValue = 0L
        var invested = 0.0
        val lines = portfolio.holdings.entries.mapNotNull { (symbol, holding) ->
            val q = quotes.firstOrNull { it.symbol == symbol } ?: return@mapNotNull null
            val holdingValue = q.price * holding.shares
            val holdingInvested = holding.avgCost * holding.shares
            marketValue += holdingValue
            invested += holdingInvested
            val pl = holdingValue - holdingInvested.toLong()
            "• $symbol ×${holding.shares} • avg ${holding.avgCost.toInt()} • nilai $holdingValue • P/L ${if (pl >= 0) "+" else ""}$pl"
        }
        val totalPl = marketValue - invested.toLong()
        val body = if (lines.isEmpty()) "Belum ada saham. Pilih jumlah lalu beli dari market." else lines.joinToString("\n")
        portfolioText.text = "PORTFOLIO • Modal ${invested.toLong()} • Nilai $marketValue • P/L ${if (totalPl >= 0) "+" else ""}$totalPl\n$body"
    }

    private fun trade(symbol: String, side: String, priceText: TextView) {
        statusText.text = "Memproses ${side.lowercase(Locale.US)} $tradeQuantity $symbol..."
        CloudEconomyManager.trade(this, symbol, side, tradeQuantity) { result ->
            runOnUiThread {
                result.onSuccess { trade ->
                    walletText.text = "💰 ${trade.coins} RAPO Coin"
                    priceText.text = "Harga server ${trade.price} • Pegangan ${trade.shares} lembar • avg ${trade.avgCost.toInt()}"
                    statusText.text = if (side == "BUY") "✅ $symbol dibeli ${tradeQuantity}×." else "✅ $symbol dijual ${tradeQuantity}×."
                    refreshPortfolio()
                }.onFailure { statusText.text = "Gagal transaksi: ${it.message ?: "error"}" }
            }
        }
    }
}
