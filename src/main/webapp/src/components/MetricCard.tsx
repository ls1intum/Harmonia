import type {SubMetric} from "@/types/team";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";

interface MetricCardProps {
    metric: SubMetric;
}

const MetricCard = ({ metric }: MetricCardProps) => {
    const getProgressColor = (value: number) => {
        if (value >= 80) return "bg-success";
        if (value >= 60) return "bg-warning";
        return "bg-destructive";
    };

    return (
        <Card className="shadow-card hover:shadow-elevated transition-shadow">
            <CardHeader>
                <div className="flex items-start justify-between">
                    <div className="space-y-1 flex-1">
                        <CardTitle className="text-lg">{metric.name}</CardTitle>
                        <CardDescription className="text-sm">
                            {metric.description}
                        </CardDescription>
                    </div>
                    <div className="flex flex-col items-end gap-1 ml-4">
                        <span className="text-3xl font-bold">{metric.value}</span>
                        <span className="text-xs text-muted-foreground">{metric.weight}% weight</span>
                    </div>
                </div>
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="space-y-2">
                    <div className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">Score</span>
                        <span className="font-medium">{metric.value}/100</span>
                    </div>
                    <Progress
                        value={metric.value}
                        className="h-2 bg-muted/50"
                        indicatorClassName={getProgressColor(metric.value)}
                    />
                </div>
                <div className="pt-2 border-t">
                    <div className="text-sm text-muted-foreground leading-relaxed space-y-1">
                        {metric.details.split('.').filter(s => s.trim()).map((sentence, idx) => (
                            <p key={idx} className="flex items-start gap-2">
                                <span className="text-primary mt-0.5">•</span>
                                <span>{sentence.trim()}</span>
                            </p>
                        ))}
                    </div>
                </div>
            </CardContent>
        </Card>
    );
};

export default MetricCard;
