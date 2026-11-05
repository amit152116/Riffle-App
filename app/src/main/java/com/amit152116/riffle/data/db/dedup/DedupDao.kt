package com.amit152116.riffle.data.db.dedup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

class ApprovedGroupContent(val groupId: Long, val audioContentId: Long)

@Dao
interface DedupDao {
    @Insert
    suspend fun insertDupGroup(dupGroup: DupGroup): Long

    @Insert
    suspend fun insertGroupContent(groupContent: GroupContent)

    @Insert
    suspend fun insertGroupMember(groupMember: GroupMember)

    @Insert
    suspend fun insertQuarantineLog(quarantineLog: QuarantineLog): Long

    @Query("SELECT * FROM DupGroup WHERE id = :id")
    suspend fun getDupGroupById(id: Long): DupGroup?

    @Query("SELECT * FROM GroupMember WHERE groupId = :groupId AND trackId = :trackId")
    suspend fun getGroupMember(groupId: Long, trackId: Long): GroupMember?

    @Query("SELECT * FROM QuarantineLog WHERE id = :id")
    suspend fun getQuarantineLogById(id: Long): QuarantineLog?

    /** Recordings belonging to a group the user approved (or that was already applied). */
    @Query(
        "SELECT gc.groupId AS groupId, gc.audioContentId AS audioContentId " +
            "FROM GroupContent gc JOIN DupGroup dg ON dg.id = gc.groupId " +
            "WHERE dg.decision IN ('APPROVED', 'APPLIED')"
    )
    suspend fun getApprovedGroupContent(): List<ApprovedGroupContent>
}
