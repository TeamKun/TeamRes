package net.kunmc.lab.teamres

import com.flylib.flylib3.FlyLib
import com.flylib.flylib3.FlyLibComponent
import com.flylib.flylib3.util.event
import com.flylib.flylib3.util.ready
import net.kunmc.lab.teamres.syncable.Syncable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class TeamManager(override val flyLib: FlyLib) : FlyLibComponent {
    private val teams = mutableListOf<ResTeamImpl>()

    fun getTeamByName(name: String): ResTeamImpl? {
        return teams.find { it.toString() == name }
    }

    fun getTeam(p: OfflinePlayer): ResTeamImpl? = teams.find { it.all().contains(p) }

    fun teams() = teams.toList()

    fun genTeam(teamName: Component, leader: OfflinePlayer, vararg members: OfflinePlayer): ResTeam {
        val team = ResTeamImpl(leader, members.toList(), teamName, flyLib)
        teams.add(team)
        return team
    }

    /**
     * Set Syncable Status to All Team
     */
    fun setSync(syncable: Syncable, onOff: OnOff) {
        teams.forEach { it.effect(syncable, onOff) }
    }
}

/**
 * Express ResTeam,
 * @note Even if the player in this team gets offline,the player will not be removed from the team.
 */
final class ResTeamImpl(
    leader: OfflinePlayer,
    members: List<OfflinePlayer>,
    val teamName: Component,
    override val flyLib: FlyLib
) : ResTeam, FlyLibComponent {
    init {
        ready {
            registerTasks()
        }
    }

    /**
     * Internal Leader variable
     */
    private var led = leader

    /**
     * Internal Members variable
     */
    private var mes = members

    private fun registerTasks() {
        event<PlayerJoinEvent, Unit> {
            // Player join event
            if (all().contains(it.player)) {
                // If player is in team, need to effect
                affected().forEach { s ->
                    s.startSync(this@ResTeamImpl, it.player)
                }
            }
        }

        event<PlayerQuitEvent, Unit> {
            // Player quit event
            if (all().contains(it.player)) {
                // If player is in team, need to effect
                affected().forEach { s ->
                    s.endSync(this@ResTeamImpl, it.player)
                }

                if (led == it.player) {
                    // If player is leader, need to make notice to change leader
                    Bukkit.broadcast(teamName.append(Component.text("????????????????????????????????????")))
                }
            }
        }
    }

    override fun all(): List<OfflinePlayer> = listOf(led, *(mes.toTypedArray()))

    override fun getMembers(): List<OfflinePlayer> = mes.toList()

    override fun getLeader(): OfflinePlayer = led

    override fun changeLeader(next: OfflinePlayer) {
        remove(led)
        add(next)
        led = next
    }

    override fun add(p: OfflinePlayer) {
        if (all().contains(p)) return

        affected().forEach {
            it.startSync(this, p)
        }

        mes = listOf(p, *(mes.toTypedArray()))
    }

    override fun remove(p: OfflinePlayer) {
        if (!all().contains(p)) return

        affected().forEach {
            it.endSync(this, p)
        }

        mes = mes.filter { it != p }.toList()
    }

    private val effected = mutableListOf<Syncable>()

    override fun affected(): List<Syncable> = effected.toList()
    override fun effect(syncable: Syncable, isOnOff: OnOff) {
        if (isOnOff.isOn) {
            syncable.startSync(this)
            effected.add(syncable)
        } else {
            syncable.endSync(this)
            effected.remove(syncable)
        }
    }

    override fun toString(): String {
        if (this.teamName is TextComponent) {
            return this.teamName.content()
        }
        return this.teamName.toString()
    }
}