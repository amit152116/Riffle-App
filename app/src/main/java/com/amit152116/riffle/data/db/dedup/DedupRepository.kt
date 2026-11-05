package com.amit152116.riffle.data.db.dedup

class DedupRepository(private val dao: DedupDao) {
    suspend fun insertDupGroup(dupGroup: DupGroup) = dao.insertDupGroup(dupGroup)
    suspend fun insertGroupContent(groupContent: GroupContent) = dao.insertGroupContent(groupContent)
    suspend fun insertGroupMember(groupMember: GroupMember) = dao.insertGroupMember(groupMember)
    suspend fun insertQuarantineLog(quarantineLog: QuarantineLog) = dao.insertQuarantineLog(quarantineLog)
    suspend fun getDupGroupById(id: Long) = dao.getDupGroupById(id)
    suspend fun getGroupMember(groupId: Long, trackId: Long) = dao.getGroupMember(groupId, trackId)
    suspend fun getQuarantineLogById(id: Long) = dao.getQuarantineLogById(id)

    /** recording -> the other recordings it must never share a shuffle with (approved/applied groups only). */
    suspend fun getDuplicateExclusions(): Map<Long, Set<Long>> {
        val byGroup = dao.getApprovedGroupContent().groupBy({ it.groupId }, { it.audioContentId })
        val out = HashMap<Long, MutableSet<Long>>()
        for (members in byGroup.values) {
            for (id in members) out.getOrPut(id) { HashSet() }.addAll(members.filter { it != id })
        }
        return out
    }
}
