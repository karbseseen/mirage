package p2p.base

import config.{Config, Language}
import constant.Translate
import core.TaskQueue.scheduleSingleAt
import javafx.beans.binding.StringBinding
import scalafx.Includes.jfxObservableValue2sfx
import util.let

import java.time.LocalDateTime
import scala.util.{Random, Try}


private object Nick:
  import NickTr.*

  private[p2p] trait Trait:
    def nickConfig: Config.StringProp = config
    def nickActual: StringBinding = binding

  private val config = Config.StringProp(this, "nick")
  private val binding: StringBinding = new StringBinding:
    protected def computeValue: String =
      if (config().isBlank) Try(getRandom).getOrElse("Nick error")
      else config()
    bind(config)

  private[p2p] var _value = binding()
  binding.addListener: (_,_,nick) =>
    scheduleSingleAt(0):
      _value = nick
      P2p.peers.values.foreach(_.ping())

  private def getRandom: String =
    val now = LocalDateTime.now
    val random = Random()

    val isNight = now.getHour.let(hour => hour >= 22 || hour < 6)
    val adjectives = if (isNight) nightAdjectives else NickTr.adjectives
    val nouns = if (random.nextInt(3) == 0) characterNouns else animalNouns

    val adjectiveStr = adjectives(random.nextInt(adjectives.length))(Language.config())
    val nounStr = nouns(random.nextInt(nouns.length))(Language.config())

    val (noun, nounGender) = nounStr.split('^') match
      case Array(noun) => (noun, None)
      case Array(noun, gender) => (noun, Some(gender))

    val adjective = adjectiveStr.split('|').map(_.split('^')).collectFirst:
      case Array(adjective) if nounGender.isEmpty => adjective
      case Array(adjective, adjectiveGender) if nounGender.contains(adjectiveGender) => adjective

    s"${adjective.get} $noun"


private object NickTr:

  val characterNouns: IArray[Translate] = IArray(
    Translate("Sponge Bob", "Губка боб^м"),
    Translate("Squidward", "Сквидвард^м"),
    Translate("Mr. Krabs", "мистер Крабс^м"),
    Translate("Plankton", "Планктон^м"),
    Translate("CatDog", "Котопёс^м"),
    Translate("Shrek", "Шрек^м"),
    Translate("Harry Potter", "Гарри Поттер^м"),
    Translate("Dumbledore", "Дамблдор^м"),
    Translate("Dobby", "Добби^м"),
    Translate("Pikachu", "Пикачу^м"),
    Translate("Batman", "Бэтмен^м"),
    Translate("Chewie", "Чубакка^м"),
    Translate("Darth Vader", "Дарт Вейдер^м"),
    Translate("Grinch", "Гринч^м"),
    Translate("Frodo", "Фродо^м"),
  )

  val animalNouns: IArray[Translate] = IArray(
    // Млекопитающие
    Translate("hedgehog", "ёжик^м"),
    Translate("killer whale", "касатка^ж"),
    Translate("badger", "барсук^м"),
    Translate("otter", "выдра^ж"),
    Translate("fox", "лиса^ж"),
    Translate("bear", "медведь^м"),
    Translate("beaver", "бобр^м"),
    Translate("raccoon", "енот^м"),
    Translate("lynx", "рысь^ж"),
    Translate("elk", "лось^м"),
    Translate("deer", "олень^м"),
    Translate("boar", "кабан^м"),
    Translate("bat", "летучая мышь^ж"),
    Translate("hamster", "хомяк^м"),
    Translate("chinchilla", "шиншилла^ж"),
    Translate("weasel", "ласка^ж"),
    Translate("ermine", "горностай^м"),
    Translate("wolverine", "росомаха^ж"),
    Translate("chipmunk", "бурундук^м"),
    Translate("gopher", "суслик^м"),
    Translate("marmot", "сурок^м"),
    Translate("lemming", "лемминг^м"),
    Translate("porcupine", "дикобраз^м"),
    Translate("capybara", "капибара^ж"),
    Translate("armadillo", "броненосец^м"),
    Translate("anteater", "муравьед^м"),
    Translate("sloth", "ленивец^м"),
    Translate("camel", "верблюд^м"),
    Translate("llama", "лама^ж"),
    Translate("alpaca", "альпака^ж"),
    Translate("giraffe", "жираф^м"),
    Translate("zebra", "зебра^ж"),
    Translate("hippo", "бегемот^м"),
    Translate("rhino", "носорог^м"),
    Translate("tiger", "тигр^м"),
    Translate("leopard", "леопард^м"),
    Translate("cheetah", "гепард^м"),
    Translate("panther", "пантера^ж"),
    Translate("hyena", "гиена^ж"),
    Translate("jackal", "шакал^м"),
    Translate("meerkat", "сурикат^м"),
    Translate("mongoose", "мангуст^м"),
    Translate("lemur", "лемур^м"),
    Translate("orangutan", "орангутан^м"),
    Translate("panda", "панда^ж"),
    Translate("yak", "як^м"),
    Translate("pony", "пони^м"),
    Translate("donkey", "осёл^м"),
    Translate("dolphin", "дельфин^м"),
    Translate("seal", "тюлень^м"),
    Translate("manatee", "ламантин^м"),
    Translate("platypus", "утконос^м"),
    Translate("wombat", "вомбат^м"),
    Translate("tapir", "тапир^м"),
    // Птицы
    Translate("owl", "сова^ж"),
    Translate("eagle", "орёл^м"),
    Translate("crow", "ворона^ж"),
    Translate("raven", "ворон^м"),
    Translate("swan", "лебедь^м"),
    Translate("penguin", "пингвин^м"),
    Translate("pelican", "пеликан^м"),
    Translate("toucan", "тукан^м"),
    Translate("kingfisher", "зимородок^м"),
    Translate("woodpecker", "дятел^м"),
    Translate("falcon", "сокол^м"),
    Translate("ostrich", "страус^м"),
    // Рептилии, амфибии и прочие
    Translate("turtle", "черепаха^ж"),
    Translate("chameleon", "хамелеон^м"),
    Translate("gecko", "геккон^м"),
    Translate("iguana", "игуана^ж"),
    Translate("frog", "лягушка^ж"),
    Translate("axolotl", "аксолотль^м"),
    Translate("octopus", "осьминог^м"),
    Translate("jellyfish", "медуза^ж"),
    Translate("seahorse", "морской конёк^м"),
    Translate("ladybug", "божья коровка^ж"),
    Translate("dragonfly", "стрекоза^ж"),
    Translate("kraken", "кракен^м"),
    Translate("unicorn", "единорог^м"),
  )

  val adjectives: IArray[Translate] = IArray(
    Translate("Unknown", "Неопознанный^м|Неопознанная^ж"),
    Translate("Mysterious", "Таинственный^м|Таинственная^ж"),
    Translate("Cosmic", "Космический^м|Космическая^ж"),
  )

  val nightAdjectives: IArray[Translate] = IArray(
    Translate("Sleepy", "Сонный^м|Сонная^ж"),
    Translate("Midnight", "Полуночный^м|Полуночная^ж"),
  )
