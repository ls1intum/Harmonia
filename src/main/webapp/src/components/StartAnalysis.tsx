import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PlayCircle } from "lucide-react";

interface StartAnalysisProps {
    onStart: (course: string, exercise: string) => void;
}

const StartAnalysis = ({ onStart }: StartAnalysisProps) => {
    const [course, setCourse] = useState("ITP");
    const [exercise, setExercise] = useState("Final Project");

    const handleCourseChange = (value: string) => {
        setCourse(value);
        setExercise(""); // Reset exercise when course changes
    };

    const handleStart = () => {
        if (course && exercise) {
            onStart(course, exercise);
        }
    };

    return (
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-6 px-4">
            <div className="text-center space-y-4 max-w-2xl">
                <h1 className="text-4xl md:text-5xl font-bold bg-gradient-primary bg-clip-text">
                    Welcome to Harmonia!
                </h1>
                <p className="text-lg text-muted-foreground">
                    Analyze student team projects to assess collaboration quality
                </p>
            </div>

            <div className="w-full max-w-md space-y-4 mt-4">
                <div className="space-y-2">
                    <Label htmlFor="course">Course</Label>
                    <Select value={course} onValueChange={handleCourseChange}>
                        <SelectTrigger id="course">
                            <SelectValue placeholder="Select a course" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ITP">ITP</SelectItem>
                            <SelectItem value="DevOps">DevOps</SelectItem>
                        </SelectContent>
                    </Select>
                </div>

                <div className="space-y-2">
                    <Label htmlFor="exercise">Exercise</Label>
                    <Select value={exercise} onValueChange={setExercise} disabled={!course}>
                        <SelectTrigger id="exercise">
                            <SelectValue placeholder="Select an exercise" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="Final Project">Final Project</SelectItem>
                        </SelectContent>
                    </Select>
                </div>

                <Button
                    size="lg"
                    onClick={handleStart}
                    disabled={!course || !exercise}
                    className="w-full mt-4 text-lg px-8 py-6 shadow-elevated hover:shadow-card transition-all"
                >
                    <PlayCircle className="mr-2 h-5 w-5" />
                    Start Analysis
                </Button>
            </div>
        </div>
    );
};

export default StartAnalysis;
