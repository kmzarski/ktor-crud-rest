import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Table

object AppUsers : Table() {
    val userid = integer("userid").autoIncrement().primaryKey()
    val firstName = varchar("firstName", 50)
    val email = varchar("email", 50)
    val description = varchar("description", 250)
    val password = varchar("password", 50)

}
