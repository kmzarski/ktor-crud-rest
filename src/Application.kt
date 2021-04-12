package com.example

import AppUsers
import Cars
import com.fasterxml.jackson.databind.SerializationFeature
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.*


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

//Te klasy "data class" mozna zrozumiec jako dto, tylko "data class" mozna odbierac/wysylkac restem
data class AppUser(
    val userid: Int = 0,
    val firstName: String,
    val email: String,
    val password: String,
    val description: String
)

data class LoginModel(val email: String, val password: String)
data class PasswordModel(val email: String, val oldPassword: String, val newPassword: String)
data class DescriptionModel(val email: String, val newDecription: String)
data class CarModel(val carId: Int, val carName: String, val description: String, val cost: Int, val userId: Int)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    // moja funckja do stawaiana H2, postgress mial problemy z ktorem, duzo zmiany konfiguracji
    // dlatego zostalem przy H2
    initH2_DB()

    // serializacja jacksonem
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    // instalowanie corsow
    install(CORS)
    {
        method(HttpMethod.Options)
        header(HttpHeaders.XForwardedProto)
        anyHost()

        allowCredentials = true
        allowNonSimpleContentTypes = true
        maxAge = Duration.ofDays(1)
    }
// kazdy kod sql musi byc w transaction{}
    transaction {
        //dodawanie logowania co robi bazka
        addLogger(StdOutSqlLogger)
        //tworzenie tabel
        SchemaUtils.create(AppUsers, Cars)


        AppUsers.insert {
            it[firstName] = "Something"
            it[email] = "kamil@wp.pl"
            it[description] = ""
            it[password] = encode("password")
        }

        Cars.insert {
            it[carName] = "Fiat 126p"
            it[cost] = 15000
            it[description] = "Kolor czerwony"
            it[Cars.userId] = 1
        }
        Cars.insert {
            it[carName] = "Fiat 126p"
            it[cost] = 15000
            it[description] = "Kolor czerwony"
            it[Cars.userId] = 1
        }

        commit()
    }

    // routing, czyli jakby controller
    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }
        post("/login") {
            val post = call.receive<LoginModel>()
            val user: ArrayList<AppUser> = arrayListOf()
            transaction {
                for (appuser in AppUsers.select { AppUsers.email.eq(post.email) }) {
                    if (appuser[AppUsers.password] == encode(post.password)) {
                        println("hasze sie zgadzaja")
                        println(appuser[AppUsers.userid])
                        user.add(
                            AppUser(
                                appuser[AppUsers.userid],
                                appuser[AppUsers.firstName],
                                appuser[AppUsers.email],
                                "",
                                appuser[AppUsers.description]

                            )
                        )
                    }
                    println("${appuser[AppUsers.firstName]}: ${appuser[AppUsers.email]}")
                }
            }
            if (user.size != 0) {
                call.respond(
                    user.get(0)
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
        post("/register") {
            val post = call.receive<AppUser>()
            println("status:")
            // 3. walidację na unikatowy login
            var status = 0;
            transaction {
                for (appuser in AppUsers.select { AppUsers.email.eq(post.email) }) {
                    status = 1
                }
            }
//            walidacja sily hasla
            if (!isStrong(post.password)) {
                println("Haslo za slabe")
                status = 1;
            }
            println("status:")
            println(status)
            if (status != 0) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                transaction {
                    AppUsers.insert {
                        it[firstName] = post.firstName
                        it[email] = post.email
                        it[password] = encode(post.password)
                        it[description] = post.description
                    }
                    commit()
                }
                println(post)
                transaction {
                    println("Manual join:")
                    ((AppUsers).selectAll()).forEach {
                        val firstName = it[AppUsers.firstName]
                        println(firstName)
                    }
                }
                call.respond(HttpStatusCode.Created)
            }
        }

        post("/user/password") {
            val post = call.receive<PasswordModel>()
            var status = 0;
            transaction {
                for (appuser in AppUsers.select { AppUsers.email.eq(post.email) }) {
                    if (appuser[AppUsers.password] == encode(post.oldPassword)) {
                        println("hasze sie zgadzaja")
                        println(appuser[AppUsers.userid])
                        AppUsers.update({ AppUsers.email eq post.email }) {
                            it[password] = encode(post.newPassword)
                        }
                    } else {
                        status = 1;
                    }
                }
            }
            if (status != 0) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
        post("/user/description") {
            val post = call.receive<DescriptionModel>()
            var status = 1;
            transaction {
                for (appuser in AppUsers.select { AppUsers.email.eq(post.email) }) {
                    println("zmiana opisu, znaleziono usera")
                    println(appuser[AppUsers.userid])
                    println("stary opis: ")
                    println(appuser[AppUsers.description])
                    AppUsers.update({ AppUsers.email eq post.email }) {
                        it[description] = post.newDecription
                    }
                    status = 0;
                    println("nowy opis: ")
                    println(post.newDecription)
                }
            }
            if (status != 0) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respond(HttpStatusCode.OK)
            }
        }
        get("/user/{id}") {
            val users: ArrayList<AppUser> = arrayListOf()
            val id: Int = call.parameters["id"]!!.toInt()
            transaction {
                for (appuser in AppUsers.select { AppUsers.userid.eq(id) }) {
                    users.add(
                        AppUser(
                            appuser[AppUsers.userid],
                            appuser[AppUsers.firstName],
                            appuser[AppUsers.email],
                            "",
                            appuser[AppUsers.description]
                        )
                    )
                }
            }
            println(users)
            call.respond(users.get(0))


        }

//get all cars - not used
        get("/cars/all") {
            val cars: ArrayList<CarModel> = arrayListOf()
            transaction {
                for (car in Cars.select { Cars.carid.isNotNull() }) {
                    cars.add(
                        CarModel(
                            car[Cars.carid],
                            car[Cars.carName],
                            car[Cars.description],
                            car[Cars.cost],
                            1// !!!!!!!! - po zmianie nie bedzie poprawnie dzialac chyba
                        )
                    )
                }
            }
            println(cars)
            call.respond(cars)
        }
        // get cars by userid

        get("/cars/all/{id}") {
            val cars: ArrayList<CarModel> = arrayListOf()
            val id: Int = call.parameters["id"]!!.toInt()
            transaction {
                for (car in Cars.select { Cars.userId.eq(id) }) {
                    cars.add(
                        CarModel(
                            car[Cars.carid],
                            car[Cars.carName],
                            car[Cars.description],
                            car[Cars.cost],
                            id
                        )
                    )
                }
            }
            println(cars)
            call.respond(cars)
        }
        // get car by id
        get("/cars/{id}") {
            val cars: ArrayList<CarModel> = arrayListOf()
            val id: Int = call.parameters["id"]!!.toInt()

            transaction {
                for (car in Cars.select { Cars.carid.eq(id) }) {
                    cars.add(
                        CarModel(
                            car[Cars.carid],
                            car[Cars.carName],
                            car[Cars.description],
                            car[Cars.cost],
                            id
                        )
                    )
                }
            }
            println(cars)
            call.respond(cars.get(0))
        }

        post("/cars/add") {
            val post = call.receive<CarModel>()

            transaction {

                Cars.insert {
                    it[carName] = post.carName
                    it[description] = post.description
                    it[cost] = post.cost
                    it[userId] = post.userId
                }
                commit()

            }
            call.respond(HttpStatusCode.Created)

        }
        // TO DO ediT car by id
        post("/cars/edit") {
            val post = call.receive<CarModel>()
            transaction {
                Cars.update({ Cars.carid eq post.carId }) {
                    it[carName] = post.carName
                    it[description] = post.description
                    it[cost] = post.cost
                }
            }
            call.respond(HttpStatusCode.OK)
        }
        // TO DO DELETE CAR
        post("/cars/delete") {
            val post = call.receive<CarModel>()
            transaction {
                Cars.deleteWhere { Cars.carid eq post.carId }
            }
            call.respond(HttpStatusCode.OK)
        }


    }
}
//funkcja od-haszujaca string
private fun decode(encodedString: String?) = Base64.getDecoder().decode(encodedString)

//funkcja haszujaca string
private fun encode(oriString: String) =
    Base64.getEncoder().withoutPadding().encodeToString(oriString.toByteArray())

fun initH2_DB() {
    Database.connect(url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
}

// gdyby ktos potrzebowal konfiguracji postgressa
fun initPG_DB() {
    val config = HikariConfig("/hikari.properties")
    config.schema = "publ1ic"
    val ds = HikariDataSource(config)
    Database.connect(ds)

}

//funkcja sprawdzajaca czy haslo jest silne
fun isStrong(password: String): Boolean {
    val n = password.length
    var hasUpper = false
    var specialChar = false
    val set: Set<Char> = HashSet(
        Arrays.asList(
            '!', '@', '#', '$', '%', '^', '&',
            '*', '(', ')', '-', '+'
        )
    )
    for (i in password.toCharArray()) {
        if (Character.isUpperCase(i)) hasUpper = true
        if (set.contains(i)) specialChar = true
    }

    return (hasUpper && specialChar
            && n >= 5)

}