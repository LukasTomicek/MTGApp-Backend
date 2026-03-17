package mtg.app.feature.wallet.domain

import mtg.app.core.error.ValidationException

class CreditProductCatalog {
    private val products = mapOf(
        WalletPlatform.ANDROID to mapOf(
            "mtglocaltrade.credits.10" to 10,
            "mtglocaltrade.credits.50" to 50,
            "mtglocaltrade.credits.100" to 100,
        ),
        WalletPlatform.IOS to mapOf(
            "mtglocaltrade.credits.10" to 10,
            "mtglocaltrade.credits.50" to 50,
            "mtglocaltrade.credits.100" to 100,
        ),
    )

    fun creditsFor(platform: WalletPlatform, productId: String): Int {
        return products[platform]
            ?.get(productId.trim())
            ?: throw ValidationException("Unsupported credit product")
    }
}
