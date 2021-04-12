import org.jetbrains.exposed.sql.Table

// DAO
object Cars : Table() {
    val carid = integer("carid").autoIncrement().primaryKey()
    val carName = varchar("carName", 50)
    val cost = integer("cost")
    val description = varchar("description", 250)
    //przyklad zrobienia n-1, wiele aut ma 1 usera
    val userId =(integer("userId") references AppUsers.userid).nullable() // Column<Int?>

}