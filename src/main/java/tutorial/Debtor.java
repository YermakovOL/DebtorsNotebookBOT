package tutorial;

import java.util.Date;

public class Debtor {
    private String name;
    private final long id;
    private int debt;
    private GrowthCycle growthCycle;
    private int percent;
    private String photo;

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    @Override
    public String toString() {
        return "Debtor{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", debt=" + debt +
                ", growthCyclel=" + growthCycle +
                ", percent=" + percent +
                ", start=" + start +
                ", description='" + description + '\'' +
                "photo: " + photo + '}';
    }

    public void setGrowthCycle(GrowthCycle growthCycle) {
        this.growthCycle = growthCycle;
    }

    private final Date start;


    private String description;


    public void setDescription(String description) {
        this.description = description;
    }

    public Debtor(Long ID) {
        id = ID;
        start = new Date();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDebt(int debt) {
        this.debt = debt;
    }


    public void setPercent(int percent) {
        this.percent = percent;
    }

    public String getPhoto() {
        return photo;
    }
}
