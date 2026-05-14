package com.example.nettools.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class CellTower(
    val tech: String,          // 2G / 3G / 4G / 5G
    val operatorPlmn: String,  // MCC+MNC
    val cellId: Long?,         // CI / CID / NCI
    val pci: Int?,             // 物理小区 ID (LTE/NR/TDSCDMA)
    val tacLac: Int?,          // TAC (LTE/NR) 或 LAC (GSM/WCDMA)
    val arfcn: Int?,           // ARFCN/EARFCN/NRARFCN
    val band: String?,         // 频段（如 B3、B41、n78）
    val dbm: Int?,             // null = 未知
    val level: Int,            // 0..4
    val isServing: Boolean,    // 当前驻留小区
    val rsrp: Int? = null,     // LTE/NR
    val rsrq: Int? = null,
    val sinr: Int? = null,     // LTE: rssnr (dB×10) / NR: dB
)

// Android 用 Int.MAX_VALUE / Long.MAX_VALUE 表示"未知/无效"
private fun Int.clean(): Int? =
    if (this == Int.MAX_VALUE || this == Int.MIN_VALUE) null else this
private fun Long.clean(): Long? =
    if (this == Long.MAX_VALUE || this == Long.MIN_VALUE || this == Int.MAX_VALUE.toLong()) null else this

object CellScanner {

    fun supported(ctx: Context): Boolean {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm != null && tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }

    fun operatorName(ctx: Context): String? {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.networkOperatorName?.takeIf { it.isNotBlank() }
    }

    /** 定时轮询周围基站 */
    @SuppressLint("MissingPermission")
    fun stream(ctx: Context, intervalMs: Long = 3_000): Flow<List<CellTower>> = flow {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        while (true) {
            val towers = runCatching {
                val infos: List<CellInfo> = tm.allCellInfo ?: emptyList()
                infos.mapNotNull(::parse)
                    .sortedWith(compareByDescending<CellTower> { it.isServing }
                        .thenByDescending { it.dbm })
            }.getOrDefault(emptyList())
            emit(towers)
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun parse(info: CellInfo): CellTower? = runCatching {
        when {
            info is CellInfoLte -> parseLte(info)
            info is CellInfoGsm -> parseGsm(info)
            info is CellInfoWcdma -> parseWcdma(info)
            Build.VERSION.SDK_INT >= 29 && info is CellInfoNr -> parseNr(info)
            Build.VERSION.SDK_INT >= 29 && info is CellInfoTdscdma -> parseTdscdma(info)
            else -> null
        }
    }.getOrNull()

    private fun parseLte(c: CellInfoLte): CellTower {
        val id = c.cellIdentity
        val s = c.cellSignalStrength
        val plmn = (id.mccString.orEmpty()) + (id.mncString.orEmpty())
        val rsrp = if (Build.VERSION.SDK_INT >= 26) runCatching { s.rsrp }.getOrNull()?.clean() else null
        val rsrq = if (Build.VERSION.SDK_INT >= 26) runCatching { s.rsrq }.getOrNull()?.clean() else null
        val sinr = if (Build.VERSION.SDK_INT >= 26) runCatching { s.rssnr }.getOrNull()?.clean() else null
        val earfcn = if (Build.VERSION.SDK_INT >= 24) runCatching { id.earfcn }.getOrNull()?.clean() else null
        return CellTower(
            tech = "4G",
            operatorPlmn = plmn,
            cellId = id.ci.toLong().clean(),
            pci = id.pci.clean(),
            tacLac = id.tac.clean(),
            arfcn = earfcn,
            band = earfcn?.let { lteBand(it) },
            dbm = s.dbm.clean(),
            level = s.level,
            isServing = c.isRegistered,
            rsrp = rsrp, rsrq = rsrq, sinr = sinr,
        )
    }

    private fun parseGsm(c: CellInfoGsm): CellTower {
        val id: CellIdentityGsm = c.cellIdentity
        val s = c.cellSignalStrength
        val plmn = (id.mccString.orEmpty()) + (id.mncString.orEmpty())
        val arfcn = if (Build.VERSION.SDK_INT >= 24) runCatching { id.arfcn }.getOrNull()?.clean() else null
        return CellTower(
            tech = "2G",
            operatorPlmn = plmn,
            cellId = id.cid.toLong().clean(),
            pci = null,
            tacLac = id.lac.clean(),
            arfcn = arfcn,
            band = null,
            dbm = s.dbm.clean(),
            level = s.level,
            isServing = c.isRegistered,
        )
    }

    private fun parseWcdma(c: CellInfoWcdma): CellTower {
        val id: CellIdentityWcdma = c.cellIdentity
        val s = c.cellSignalStrength
        val plmn = (id.mccString.orEmpty()) + (id.mncString.orEmpty())
        val uarfcn = if (Build.VERSION.SDK_INT >= 24) runCatching { id.uarfcn }.getOrNull()?.clean() else null
        return CellTower(
            tech = "3G",
            operatorPlmn = plmn,
            cellId = id.cid.toLong().clean(),
            pci = id.psc.clean(),
            tacLac = id.lac.clean(),
            arfcn = uarfcn,
            band = null,
            dbm = s.dbm.clean(),
            level = s.level,
            isServing = c.isRegistered,
        )
    }

    @androidx.annotation.RequiresApi(29)
    private fun parseTdscdma(c: CellInfoTdscdma): CellTower {
        val id: CellIdentityTdscdma = c.cellIdentity
        val s = c.cellSignalStrength
        val plmn = (id.mccString.orEmpty()) + (id.mncString.orEmpty())
        return CellTower(
            tech = "3G",
            operatorPlmn = plmn,
            cellId = id.cid.toLong().clean(),
            pci = id.cpid.clean(),
            tacLac = id.lac.clean(),
            arfcn = id.uarfcn.clean(),
            band = null,
            dbm = s.dbm.clean(),
            level = s.level,
            isServing = c.isRegistered,
        )
    }

    @androidx.annotation.RequiresApi(29)
    private fun parseNr(c: CellInfoNr): CellTower {
        val id = c.cellIdentity as CellIdentityNr
        val s = c.cellSignalStrength as CellSignalStrengthNr
        val plmn = (id.mccString.orEmpty()) + (id.mncString.orEmpty())
        val nrarfcn = id.nrarfcn.clean()
        // 优先用 CSI，其次 SS；MAX_VALUE 表示无效
        val rsrp = (s.csiRsrp.clean() ?: s.ssRsrp.clean())
        val rsrq = (s.csiRsrq.clean() ?: s.ssRsrq.clean())
        val sinr = (s.csiSinr.clean() ?: s.ssSinr.clean())
        // 邻区的 dBm 经常是 MAX_VALUE，过滤掉
        val dbm = s.dbm.clean() ?: rsrp
        return CellTower(
            tech = "5G",
            operatorPlmn = plmn,
            cellId = id.nci.clean(),
            pci = id.pci.clean(),
            tacLac = id.tac.clean(),
            arfcn = nrarfcn,
            band = nrarfcn?.let { nrBand(it) },
            dbm = dbm,
            level = s.level,
            isServing = c.isRegistered,
            rsrp = rsrp, rsrq = rsrq, sinr = sinr,
        )
    }

    /** 把 EARFCN 转成 LTE Band（仅常用 FDD/TDD 频段） */
    private fun lteBand(earfcn: Int): String? = when (earfcn) {
        in 0..599 -> "B1"
        in 600..1199 -> "B2"
        in 1200..1949 -> "B3"
        in 1950..2399 -> "B4"
        in 2400..2649 -> "B5"
        in 2750..3449 -> "B7"
        in 3450..3799 -> "B8"
        in 6150..6449 -> "B20"
        in 36200..36349 -> "B34"
        in 36950..37549 -> "B38"
        in 37550..38249 -> "B39"
        in 38250..39649 -> "B40"
        in 39650..41589 -> "B41"
        in 65536..66435 -> "B66"
        else -> null
    }

    /** NR ARFCN -> band (粗略覆盖中国常用 n1/n3/n28/n41/n77/n78/n79/n5/n8) */
    private fun nrBand(nrarfcn: Int): String? = when (nrarfcn) {
        in 422000..434000 -> "n1"
        in 386000..398000 -> "n3"
        in 173800..178800 -> "n28"
        in 499200..537999 -> "n41"
        in 620000..680000 -> "n77"
        in 620000..653333 -> "n78"
        in 693334..733333 -> "n79"
        in 173800..178800 -> "n28"
        else -> null
    }
}
