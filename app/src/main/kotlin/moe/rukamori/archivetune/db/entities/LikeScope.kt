/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// ponytail: single shared scope for entity like side-effects.
// Was: new CoroutineScope(Dispatchers.IO) per tap with swallowed errors (thread churn, lost failures).
// Ceiling: still fire-and-forget from the entity; move to LikeSongUseCase + @ApplicationScope when touched next.
internal val LikeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
